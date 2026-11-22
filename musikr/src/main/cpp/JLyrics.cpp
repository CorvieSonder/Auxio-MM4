/*
 * Copyright (c) 2026 Auxio Project
 * JLyrics.cpp is part of Auxio.
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
#include "JInputStream.h"
#include "util.h"

#include "taglib/fileref.h"
#include "taglib/flacfile.h"
#include "taglib/id3v2tag.h"
#include "taglib/mp4file.h"
#include "taglib/mp4tag.h"
#include "taglib/mpegfile.h"
#include "taglib/mpegproperties.h"
#include "taglib/opusfile.h"
#include "taglib/synchronizedlyricsframe.h"
#include "taglib/unsynchronizedlyricsframe.h"
#include "taglib/vorbisfile.h"
#include "taglib/wavfile.h"
#include "taglib/xiphcomment.h"

namespace {

// Every lyrics block is returned as four strings: kind, language, description, text.
// Kinds are mirrored in LyricsJNI.kt. Synced text is one "timeMs\ttext" pair per line.
const char *KIND_PLAIN = "plain";
const char *KIND_SYNCED = "synced";

struct Block {
    std::string kind;
    TagLib::String language;
    TagLib::String description;
    TagLib::String text;
};

// NewStringUTF expects modified UTF-8, which breaks on characters outside the BMP,
// so build the Java string from UTF-16 instead.
jstring toJString(JNIEnv *env, const TagLib::String &string) {
    TagLib::ByteVector bytes = string.data(TagLib::String::UTF16BE);
    std::vector < jchar > chars(bytes.size() / 2);
    for (size_t i = 0; i < chars.size(); ++i) {
        chars[i] = (static_cast<unsigned char>(bytes[i * 2]) << 8)
                | static_cast<unsigned char>(bytes[i * 2 + 1]);
    }
    return env->NewString(chars.data(), static_cast<jsize>(chars.size()));
}

TagLib::String languageOf(const TagLib::ByteVector &language) {
    return TagLib::String(language, TagLib::String::Latin1).stripWhiteSpace();
}

int samplesPerMpegFrame(const TagLib::MPEG::Properties *properties) {
    if (properties == nullptr) {
        return 0;
    }
    switch (properties->layer()) {
    case 1:
        return 384;
    case 2:
        return 1152;
    default:
        return properties->version() == TagLib::MPEG::Header::Version1 ?
                1152 : 576;
    }
}

void readId3v2(TagLib::ID3v2::Tag *tag,
        const TagLib::MPEG::Properties *properties,
        std::vector<Block> &blocks) {
    if (tag == nullptr) {
        return;
    }
    const auto &frames = tag->frameListMap();
    auto uslt = frames.find("USLT");
    if (uslt != frames.end()) {
        for (auto *frame : uslt->second) {
            auto *lyrics =
                    dynamic_cast<TagLib::ID3v2::UnsynchronizedLyricsFrame*>(frame);
            if (lyrics == nullptr
                    || lyrics->text().stripWhiteSpace().isEmpty()) {
                continue;
            }
            blocks.push_back( { KIND_PLAIN, languageOf(lyrics->language()),
                    lyrics->description(), lyrics->text() });
        }
    }
    auto sylt = frames.find("SYLT");
    if (sylt != frames.end()) {
        for (auto *frame : sylt->second) {
            auto *lyrics =
                    dynamic_cast<TagLib::ID3v2::SynchronizedLyricsFrame*>(frame);
            if (lyrics == nullptr) {
                continue;
            }
            auto type = lyrics->type();
            if (type != TagLib::ID3v2::SynchronizedLyricsFrame::Lyrics
                    && type != TagLib::ID3v2::SynchronizedLyricsFrame::Other
                    && type
                            != TagLib::ID3v2::SynchronizedLyricsFrame::TextTranscription) {
                continue;
            }
            bool mpegFrames =
                    lyrics->timestampFormat()
                            == TagLib::ID3v2::SynchronizedLyricsFrame::AbsoluteMpegFrames;
            int samples = samplesPerMpegFrame(properties);
            int sampleRate =
                    properties != nullptr ? properties->sampleRate() : 0;
            if (mpegFrames && (samples == 0 || sampleRate == 0)) {
                continue;
            }
            TagLib::String text;
            for (const auto &line : lyrics->synchedText()) {
                long long timeMs = line.time;
                if (mpegFrames) {
                    timeMs = timeMs * samples * 1000 / sampleRate;
                }
                // Some taggers start each line with a line break; one line per entry here.
                TagLib::String lineText = line.text;
                lineText = lineText.stripWhiteSpace();
                text += TagLib::String(std::to_string(timeMs)) + "\t" + lineText
                        + "\n";
            }
            if (!text.isEmpty()) {
                blocks.push_back( { KIND_SYNCED, languageOf(lyrics->language()),
                        lyrics->description(), text });
            }
        }
    }
}

void readXiph(TagLib::Ogg::XiphComment *comment, std::vector<Block> &blocks) {
    if (comment == nullptr) {
        return;
    }
    const auto &fields = comment->fieldListMap();
    for (const char *key : { "LYRICS", "UNSYNCEDLYRICS" }) {
        auto field = fields.find(key);
        if (field == fields.end()) {
            continue;
        }
        for (const auto &value : field->second) {
            if (!value.stripWhiteSpace().isEmpty()) {
                blocks.push_back( { KIND_PLAIN, "", "", value });
            }
        }
    }
}

void readMp4(TagLib::MP4::Tag *tag, std::vector<Block> &blocks) {
    if (tag == nullptr || !tag->contains("\251lyr")) {
        return;
    }
    for (const auto &value : tag->item("\251lyr").toStringList()) {
        if (!value.stripWhiteSpace().isEmpty()) {
            blocks.push_back( { KIND_PLAIN, "", "", value });
        }
    }
}

std::vector<Block> readBlocks(TagLib::File *file) {
    std::vector<Block> blocks;
    if (auto *mpegFile = dynamic_cast<TagLib::MPEG::File*>(file)) {
        readId3v2(mpegFile->ID3v2Tag(), mpegFile->audioProperties(), blocks);
    } else if (auto *flacFile = dynamic_cast<TagLib::FLAC::File*>(file)) {
        readXiph(flacFile->xiphComment(), blocks);
    } else if (auto *opusFile = dynamic_cast<TagLib::Ogg::Opus::File*>(file)) {
        readXiph(opusFile->tag(), blocks);
    } else if (auto *vorbisFile = dynamic_cast<TagLib::Ogg::Vorbis::File*>(file)) {
        readXiph(vorbisFile->tag(), blocks);
    } else if (auto *wavFile = dynamic_cast<TagLib::RIFF::WAV::File*>(file)) {
        readId3v2(wavFile->ID3v2Tag(), nullptr, blocks);
    } else if (auto *mp4File = dynamic_cast<TagLib::MP4::File*>(file)) {
        readMp4(mp4File->tag(), blocks);
    }
    return blocks;
}

} // namespace

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_oxycblt_musikr_lyrics_LyricsJNI_readNative(JNIEnv *env,
        jobject /* this */,
        jobject inputStream) {
    std::string name = "unknown file";
    try {
        JInputStream jStream {env, inputStream};
        name = jStream.name();
        TagLib::FileRef fileRef {&jStream, true, TagLib::AudioProperties::Fast};
        if (fileRef.file() == nullptr) {
            return nullptr;
        }
        std::vector<Block> blocks = readBlocks(fileRef.file());
        jclass stringClass = env->FindClass("java/lang/String");
        auto result = env->NewObjectArray(static_cast<jsize>(blocks.size() * 4),
                stringClass, nullptr);
        for (size_t i = 0; i < blocks.size(); ++i) {
            const Block &block = blocks[i];
            jstring values[] = {env->NewStringUTF(block.kind.c_str()),
                toJString(env, block.language),
                toJString(env, block.description),
                toJString(env, block.text)};
            for (int j = 0; j < 4; ++j) {
                env->SetObjectArrayElement(result, static_cast<jsize>(i * 4 + j), values[j]);
                env->DeleteLocalRef(values[j]);
            }
        }
        env->DeleteLocalRef(stringClass);
        return result;
    } catch (std::exception &e) {
        LOGE("Unable to read lyrics in %s: %s", name.c_str(), e.what());
        return nullptr;
    }
}
