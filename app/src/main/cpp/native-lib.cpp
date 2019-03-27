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

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_fxyan_draco_ui_ThreeDActivity_decodeDraco(JNIEnv *jniEnv,
                                                   jobject instance,
                                                   jstring draco,
                                                   jstring outputFile,
                                                   jboolean isPly) {
    const char *cs = jniEnv->GetStringUTFChars(draco, 0);
    const char *cs1 = jniEnv->GetStringUTFChars(outputFile, 0);

    std::ifstream input_file(cs, std::ios::binary);

    // not found
    if (!input_file) {
        return false;
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
        return false;
    }

    draco::DecoderBuffer buffer;
    buffer.Init(data.data(), data.size());

    // Decode the input data into a geometry.
    std::unique_ptr<draco::PointCloud> pc;
    draco::Mesh *mesh = nullptr;
    auto type_statusor = draco::Decoder::GetEncodedGeometryType(&buffer);
    if (!type_statusor.ok()) {
        return false;
    }

    const draco::EncodedGeometryType geom_type = type_statusor.value();
    if (geom_type == draco::TRIANGULAR_MESH) {

        draco::Decoder decoder;
        auto statusor = decoder.DecodeMeshFromBuffer(&buffer);
        if (!statusor.ok()) {
            return false;
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
            return false;
        }
        pc = std::move(statusor).value();

    }

    if (pc == nullptr) {
        std::cout << "decode failed !" << std::endl;
        return false;
    } else {
        std::cout << "decode success !" << std::endl;
    }

    draco::PlyEncoder ply_encoder;
    if (mesh) {
        if (!ply_encoder.EncodeToFile(*mesh, cs1)) {
            printf("Failed to store the decoded mesh as PLY.\n");
            return false;
        }
    } else {
        if (!ply_encoder.EncodeToFile(*pc.get(), cs1)) {
            printf("Failed to store the decoded point cloud as PLY.\n");
            return false;
        }
    }

    return true;
}