#include <jni.h>
#include <iostream>
#include <fstream>
#include <string>
#include <vector>
#include <draco/compression/decode.h>
#include <draco/core/decoder_buffer.h>
#include <draco/mesh/mesh.h>
#include <draco/point_cloud/point_cloud.h>
#include <draco/compression/config/compression_shared.h>
#include <draco/io/obj_encoder.h>
#include <draco/io/ply_encoder.h>

extern "C" JNIEXPORT void JNICALL
Java_com_threed_jpark_base_LauncherActivity_decodeDraco(JNIEnv *jniEnv, jobject,
                                                        jstring fileName,
                                                        jstring outputName,
                                                        jboolean obj) {
    bool isObj = obj == JNI_TRUE;
    const char *cs = jniEnv->GetStringUTFChars(fileName, 0);
    const char *cs1 = jniEnv->GetStringUTFChars(outputName, 0);

    std::ifstream input_file(cs, std::ios::binary);

    // not found
    if (!input_file) {
        return;
    }

    // Read the file stream into a buffer.
    std::streampos file_size = 0;
    input_file.seekg(0, std::ios::end);
    file_size = input_file.tellg() - file_size;
    input_file.seekg(0, std::ios::beg);
    std::vector<char> data(file_size);
    input_file.read(data.data(), file_size);

    // file is empty
    if (data.empty()) {
        return;
    }

    draco::DecoderBuffer buffer;
    buffer.Init(data.data(), data.size());

    // Decode the input data into a geometry.
    std::unique_ptr<draco::PointCloud> pc;
    draco::Mesh *mesh = nullptr;
    auto type_statusor = draco::Decoder::GetEncodedGeometryType(&buffer);
    if (!type_statusor.ok()) {
        return;
    }

    const draco::EncodedGeometryType geom_type = type_statusor.value();
    if (geom_type == draco::TRIANGULAR_MESH) {

        draco::Decoder decoder;
        auto statusor = decoder.DecodeMeshFromBuffer(&buffer);
        if (!statusor.ok()) {
            return;
        }

        std::unique_ptr<draco::Mesh> in_mesh = std::move(statusor).value();

        if (in_mesh) {
            mesh = in_mesh.get();
            pc = std::move(in_mesh);
        }
    } else if (geom_type == draco::POINT_CLOUD) {
        // Failed to decode it as mesh, so let's try to decode it as a point cloud.
        draco::Decoder decoder;
        auto statusor = decoder.DecodePointCloudFromBuffer(&buffer);
        if (!statusor.ok()) {
            return;
        }
        pc = std::move(statusor).value();

    }

    if (pc == nullptr) {
        std::cout << "decode failed !" << std::endl;
    } else {
        std::cout << "decode success !" << std::endl;
    }

    if (isObj) {
        draco::ObjEncoder obj_encoder;
        if (mesh) {
            if (!obj_encoder.EncodeToFile(*mesh, cs1)) {
                printf("Failed to store the decoded mesh as OBJ.\n");
                return;
            }
        } else {
            if (!obj_encoder.EncodeToFile(*pc.get(), cs1)) {
                printf("Failed to store the decoded point cloud as OBJ.\n");
                return;
            }
        }
    } else {
        draco::PlyEncoder ply_encoder;
        if (mesh) {
            if (!ply_encoder.EncodeToFile(*mesh, cs1)) {
                printf("Failed to store the decoded mesh as PLY.\n");
                return;
            }
        } else {
            if (!ply_encoder.EncodeToFile(*pc.get(), cs1)) {
                printf("Failed to store the decoded point cloud as PLY.\n");
                return;
            }
        }
    }
}