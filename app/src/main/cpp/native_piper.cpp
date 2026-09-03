#include <jni.h>

#include <fstream>
#include <string>
#include <exception>

#include <piper.h>

static thread_local std::string last_error;

static std::string toString(
        JNIEnv *env,
        jstring value) {

    if (value == nullptr) {
        return {};
    }

    const char *chars =
            env->GetStringUTFChars(
                    value,
                    nullptr
            );

    if (chars == nullptr) {
        return {};
    }

    std::string result(chars);

    env->ReleaseStringUTFChars(
            value,
            chars
    );

    return result;
}

extern "C"
JNIEXPORT jlong JNICALL
Java_com_example_bibleverse_NativePiper_nativeCreate(
        JNIEnv *env,
        jclass,
        jstring model,
        jstring config,
        jstring espeakData) {

    last_error.clear();

    try {

        const std::string modelPath =
                toString(env, model);

        const std::string configPath =
                toString(env, config);

        const std::string dataPath =
                toString(env, espeakData);

        if (modelPath.empty()) {

            last_error =
                    "ONNX model path is empty.";

            return 0;
        }

        if (configPath.empty()) {

            last_error =
                    "ONNX JSON config path is empty.";

            return 0;
        }

        if (dataPath.empty()) {

            last_error =
                    "eSpeak data path is empty.";

            return 0;
        }

        piper_synthesizer *synth =
                piper_create(
                        modelPath.c_str(),
                        configPath.c_str(),
                        dataPath.c_str()
                );

        if (synth == nullptr) {

            last_error =
                    "piper_create() failed.\n\n"
                    "Check:\n"
                    "1. Ryan High ONNX file\n"
                    "2. Ryan High JSON file\n"
                    "3. espeak-ng-data\n"
                    "4. ONNX Runtime"
                    ;

            return 0;
        }

        return reinterpret_cast<jlong>(
                synth
        );

    } catch (const std::exception &e) {

        last_error =
                "Native Piper create error: ";

        last_error += e.what();

        return 0;

    } catch (...) {

        last_error =
                "Unknown native Piper create error.";

        return 0;
    }
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_example_bibleverse_NativePiper_nativeSynthesize(
        JNIEnv *env,
        jclass,
        jlong handle,
        jstring text,
        jstring outputRaw) {

    last_error.clear();

    if (handle == 0) {

        last_error =
                "Invalid Piper handle.";

        return -1;
    }

    try {

        auto *synth =
                reinterpret_cast<piper_synthesizer *>(
                        handle
                );

        const std::string textValue =
                toString(env, text);

        const std::string outputPath =
                toString(env, outputRaw);

        if (textValue.empty()) {

            last_error =
                    "Text is empty.";

            return -1;
        }

        if (outputPath.empty()) {

            last_error =
                    "Output RAW path is empty.";

            return -1;
        }

        std::ofstream output(
                outputPath,
                std::ios::binary |
                std::ios::trunc
        );

        if (!output.is_open()) {

            last_error =
                    "Cannot open RAW output file:\n" +
                    outputPath;

            return -1;
        }

        piper_synthesize_options options =
                piper_default_synthesize_options(
                        synth
                );

        const int startResult =
                piper_synthesize_start(
                        synth,
                        textValue.c_str(),
                        &options
                );

        if (startResult != PIPER_OK) {

            last_error =
                    "piper_synthesize_start() failed.\n"
                    "Return code: " +
                    std::to_string(
                            startResult
                    );

            return -1;
        }

        size_t totalSamples = 0;

        while (true) {

            piper_audio_chunk chunk{};

            const int result =
                    piper_synthesize_next(
                            synth,
                            &chunk
                    );

            if (result == PIPER_DONE) {
                break;
            }

            if (result != PIPER_OK) {

                last_error =
                        "piper_synthesize_next() failed.\n"
                        "Return code: " +
                        std::to_string(result);

                return -1;
            }

            if (chunk.samples != nullptr &&
                chunk.num_samples > 0) {

                const size_t bytes =
                        chunk.num_samples *
                        sizeof(float);

                output.write(
                        reinterpret_cast<const char *>(
                                chunk.samples
                        ),
                        static_cast<std::streamsize>(
                                bytes
                        )
                );

                if (!output) {

                    last_error =
                            "Error writing Piper RAW audio.";

                    return -1;
                }

                totalSamples +=
                        chunk.num_samples;
            }

            if (chunk.is_last) {
                break;
            }
        }

        output.close();

        if (totalSamples == 0) {

            last_error =
                    "Piper generated zero audio samples.";

            return -1;
        }

        return 0;

    } catch (const std::exception &e) {

        last_error =
                "Native Piper synthesis error: ";

        last_error += e.what();

        return -1;

    } catch (...) {

        last_error =
                "Unknown native Piper synthesis error.";

        return -1;
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_bibleverse_NativePiper_nativeFree(
        JNIEnv *,
        jclass,
        jlong handle) {

    if (handle != 0) {

        piper_free(
                reinterpret_cast<piper_synthesizer *>(
                        handle
                )
        );
    }
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_example_bibleverse_NativePiper_nativeLastError(
        JNIEnv *env,
        jclass) {

    return env->NewStringUTF(
            last_error.c_str()
    );
}
