/*
 * Copyright (c) 2026 Auxio Project
 * JTagWriter.cpp is part of Auxio.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
 
#include <jni.h>
#include <string>
#include <vector>
#include "util.h"

#include "taglib/id3v2tag.h"
#include "taglib/id3v2header.h"
#include "taglib/mpegfile.h"
#include "taglib/popularimeterframe.h"
#include "taglib/textidentificationframe.h"

namespace {

// Result codes, mirrored in TagWriterJNI.kt.
constexpr jint RESULT_OK = 0;
constexpr jint RESULT_UNSUPPORTED = 1;
constexpr jint RESULT_SAVE_FAILED = 2;
constexpr jint RESULT_VERIFY_FAILED = 3;
constexpr jint RESULT_ERROR = 4;

struct PopmEdit {
    TagLib::String email;
    int rating; // -1 removes the frame
};

struct TxxxEdit {
    TagLib::String description;
    bool remove;
    TagLib::String value;
};

TagLib::String toTagLibString(JNIEnv *env, jstring jString) {
    const char *chars = env->GetStringUTFChars(jString, nullptr);
    TagLib::String result(chars, TagLib::String::UTF8);
    env->ReleaseStringUTFChars(jString, chars);
    return result;
}

bool sameText(const TagLib::String &a, const TagLib::String &b) {
    return a.upper() == b.upper();
}

std::vector<TagLib::ID3v2::PopularimeterFrame*> popmFrames(
        TagLib::ID3v2::Tag *tag, const TagLib::String &email) {
    std::vector<TagLib::ID3v2::PopularimeterFrame*> result;
    for (auto *frame : tag->frameList("POPM")) {
        auto *popm = dynamic_cast<TagLib::ID3v2::PopularimeterFrame*>(frame);
        if (popm != nullptr && sameText(popm->email(), email)) {
            result.push_back(popm);
        }
    }
    return result;
}

std::vector<TagLib::ID3v2::UserTextIdentificationFrame*> txxxFrames(
        TagLib::ID3v2::Tag *tag, const TagLib::String &description) {
    std::vector<TagLib::ID3v2::UserTextIdentificationFrame*> result;
    for (auto *frame : tag->frameList("TXXX")) {
        auto *txxx =
                dynamic_cast<TagLib::ID3v2::UserTextIdentificationFrame*>(frame);
        if (txxx != nullptr && sameText(txxx->description(), description)) {
            result.push_back(txxx);
        }
    }
    return result;
}

void applyPopm(TagLib::ID3v2::Tag *tag, const PopmEdit &edit) {
    auto frames = popmFrames(tag, edit.email);
    if (edit.rating < 0) {
        for (auto *frame : frames) {
            tag->removeFrame(frame);
        }
        return;
    }
    if (frames.empty()) {
        auto *frame = new TagLib::ID3v2::PopularimeterFrame();
        frame->setEmail(edit.email);
        frame->setRating(edit.rating);
        frame->setCounter(0);
        tag->addFrame(frame);
        return;
    }
    // Keep the first frame (and its play counter), drop any duplicates.
    frames[0]->setRating(edit.rating);
    for (size_t i = 1; i < frames.size(); ++i) {
        tag->removeFrame(frames[i]);
    }
}

void applyTxxx(TagLib::ID3v2::Tag *tag, const TxxxEdit &edit) {
    for (auto *frame : txxxFrames(tag, edit.description)) {
        tag->removeFrame(frame);
    }
    if (edit.remove) {
        return;
    }
    auto encoding =
            edit.value.isLatin1() ?
                    TagLib::String::Latin1 : TagLib::String::UTF16;
    auto *frame = new TagLib::ID3v2::UserTextIdentificationFrame(encoding);
    frame->setDescription(edit.description);
    frame->setText(edit.value);
    tag->addFrame(frame);
}

bool verify(const char *path, const std::vector<PopmEdit> &popms,
        const std::vector<TxxxEdit> &txxxs) {
    TagLib::MPEG::File file(path, false);
    if (!file.isValid() || !file.hasID3v2Tag()) {
        return false;
    }
    TagLib::ID3v2::Tag *tag = file.ID3v2Tag();
    for (const auto &edit : popms) {
        auto frames = popmFrames(tag, edit.email);
        if (edit.rating < 0 ?
                !frames.empty() :
                frames.size() != 1 || frames[0]->rating() != edit.rating) {
            LOGE("POPM %s was not written", edit.email.toCString(true));
            return false;
        }
    }
    for (const auto &edit : txxxs) {
        auto frames = txxxFrames(tag, edit.description);
        if (edit.remove ?
                !frames.empty() :
                frames.size() != 1 || frames[0]->fieldList().size() < 2
                        || frames[0]->fieldList()[1] != edit.value) {
            LOGE("TXXX %s was not written", edit.description.toCString(true));
            return false;
        }
    }
    return true;
}

} // namespace

extern "C" JNIEXPORT jint JNICALL
Java_org_oxycblt_musikr_metadata_TagWriterJNI_writeId3Native(JNIEnv *env,
        jobject /* this */,
        jstring jPath,
        jobjectArray jPopmEmails,
        jintArray jPopmRatings,
        jobjectArray jTxxxDescriptions,
        jobjectArray jTxxxValues) {
    const char *path = env->GetStringUTFChars(jPath, nullptr);
    std::string pathCopy(path);
    env->ReleaseStringUTFChars(jPath, path);
    try {
        std::vector<PopmEdit> popms;
        jsize popmCount = env->GetArrayLength(jPopmEmails);
        jint *ratings = env->GetIntArrayElements(jPopmRatings, nullptr);
        for (jsize i = 0; i < popmCount; ++i) {
            auto jEmail = (jstring) env->GetObjectArrayElement(jPopmEmails, i);
            popms.push_back( {toTagLibString(env, jEmail), ratings[i]});
            env->DeleteLocalRef(jEmail);
        }
        env->ReleaseIntArrayElements(jPopmRatings, ratings, JNI_ABORT);

        std::vector<TxxxEdit> txxxs;
        jsize txxxCount = env->GetArrayLength(jTxxxDescriptions);
        for (jsize i = 0; i < txxxCount; ++i) {
            auto jDescription = (jstring) env->GetObjectArrayElement(jTxxxDescriptions, i);
            auto jValue = (jstring) env->GetObjectArrayElement(jTxxxValues, i);
            TxxxEdit edit {toTagLibString(env, jDescription), jValue == nullptr, {}};
            if (jValue != nullptr) {
                edit.value = toTagLibString(env, jValue);
                env->DeleteLocalRef(jValue);
            }
            env->DeleteLocalRef(jDescription);
            txxxs.push_back(edit);
        }

        TagLib::ID3v2::Version version = TagLib::ID3v2::v3;
        {
            TagLib::MPEG::File file(pathCopy.c_str(), false);
            if (!file.isValid()) {
                LOGE("Not a writable MPEG file: %s", pathCopy.c_str());
                return RESULT_UNSUPPORTED;
            }
            // Keep ID3v2.4 files at 2.4; everything else is written as 2.3.
            if (file.hasID3v2Tag() && file.ID3v2Tag()->header()->majorVersion() == 4) {
                version = TagLib::ID3v2::v4;
            }
            TagLib::ID3v2::Tag *tag = file.ID3v2Tag(true);
            for (const auto &edit : popms) {
                applyPopm(tag, edit);
            }
            for (const auto &edit : txxxs) {
                applyTxxx(tag, edit);
            }
            if (!file.save(TagLib::MPEG::File::ID3v2, TagLib::File::StripNone, version,
                            TagLib::File::DoNotDuplicate)) {
                LOGE("Unable to save %s", pathCopy.c_str());
                return RESULT_SAVE_FAILED;
            }
        }
        return verify(pathCopy.c_str(), popms, txxxs) ? RESULT_OK : RESULT_VERIFY_FAILED;
    } catch (std::exception &e) {
        LOGE("Unable to write tags to %s: %s", pathCopy.c_str(), e.what());
        return RESULT_ERROR;
    }
}
