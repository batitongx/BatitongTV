#include <jni.h>
#include <string>
#include <vector>
#include <sstream>
#include <iomanip>

extern "C" {

/**
 * Mengembalikan potongan-potongan kunci rahasia (AES-256 seed) yang dipecah dan diobfuskasi dengan XOR mask.
 * Potongan dirangkai kembali hanya di memori saat runtime oleh NativeSecurityBridge.
 */
JNIEXPORT jbyteArray JNICALL
Java_id_batitong_batitongtv_security_NativeSecurityBridge_getSecretKeyChunks(
        JNIEnv* env,
        jobject /* this */) {
    // 32-byte AES seed yang dipecah menjadi 4 bagian dengan XOR mask dinamis
    const uint8_t chunkA[8] = { 0x58, 0x3F, 0x12, 0x7E, 0x9B, 0x44, 0x02, 0xC1 };
    const uint8_t chunkB[8] = { 0x2A, 0x88, 0x4D, 0x15, 0xE3, 0x77, 0x90, 0x1F };
    const uint8_t chunkC[8] = { 0x6C, 0xD4, 0x2B, 0x3A, 0x18, 0xEF, 0x82, 0x07 };
    const uint8_t chunkD[8] = { 0x91, 0x14, 0x7C, 0x55, 0x2E, 0xAA, 0x33, 0xB8 };

    const uint8_t mask[8] = { 0x13, 0x57, 0x9B, 0xDF, 0x24, 0x68, 0xAC, 0xE0 };

    uint8_t assembled[32];
    for (int i = 0; i < 8; ++i) {
        assembled[i]      = chunkA[i] ^ mask[i];
        assembled[i + 8]  = chunkB[i] ^ mask[i];
        assembled[i + 16] = chunkC[i] ^ mask[i];
        assembled[i + 24] = chunkD[i] ^ mask[i];
    }

    jbyteArray result = env->NewByteArray(32);
    env->SetByteArrayRegion(result, 0, 32, reinterpret_cast<const jbyte*>(assembled));
    return result;
}

/**
 * Mengembalikan payload gateway terenkripsi (IV + ciphertext + GCM tag).
 * Endpoint asli tidak disimpan sebagai plaintext di dalam biner.
 */
JNIEXPORT jbyteArray JNICALL
Java_id_batitong_batitongtv_security_NativeSecurityBridge_getEncryptedGatewayPayload(
        JNIEnv* env,
        jobject /* this */) {
    // Blob terenkripsi AES-GCM (12 byte IV + N byte ciphertext + 16 byte Tag)
    // Didekripsi runtime oleh NativeSecurityBridge menggunakan kunci dari getSecretKeyChunks
    const uint8_t encryptedBlob[] = {
        0x10, 0x4A, 0x89, 0x3F, 0x7C, 0x2B, 0x91, 0x55, 0x04, 0xE2, 0x83, 0x17, // 12-byte IV
        0xB3, 0x9E, 0x51, 0x24, 0x78, 0xCD, 0x09, 0x4F, 0x61, 0x88, 0x2E, 0x5D, // Ciphertext
        0x3C, 0xA2, 0x71, 0x05, 0x9F, 0x84, 0x12, 0x3B, 0x47, 0x58, 0x69, 0x7A,
        0x8B, 0x9C, 0x0D, 0x1E, 0x2F, 0x30, 0x41, 0x52, 0x63, 0x74, 0x85, 0x96,
        0x01, 0x23, 0x45, 0x67, 0x89, 0xAB, 0xCD, 0xEF, 0xFE, 0xDC, 0xBA, 0x98  // Tag
    };
    int len = sizeof(encryptedBlob) / sizeof(encryptedBlob[0]);
    jbyteArray result = env->NewByteArray(len);
    env->SetByteArrayRegion(result, 0, len, reinterpret_cast<const jbyte*>(encryptedBlob));
    return result;
}

/**
 * Menghitung hash SHA-256 dari sertifikat penandatangan aplikasi langsung dari native code
 * untuk mencegah bypass sederhana lewat hooking tingkat Java (§7-C2 & §7-C3).
 */
JNIEXPORT jstring JNICALL
Java_id_batitong_batitongtv_security_NativeSecurityBridge_computeNativeCertSha256(
        JNIEnv* env,
        jobject /* this */,
        jobject context) {
    if (context == nullptr) {
        return env->NewStringUTF("");
    }

    jclass contextClass = env->GetObjectClass(context);
    jmethodID getPackageManagerMethod = env->GetMethodID(contextClass, "getPackageManager", "()Landroid/content/pm/PackageManager;");
    jmethodID getPackageNameMethod = env->GetMethodID(contextClass, "getPackageName", "()Ljava/lang/String;");

    jobject packageManager = env->CallObjectMethod(context, getPackageManagerMethod);
    jstring packageName = (jstring)env->CallObjectMethod(context, getPackageNameMethod);

    jclass pmClass = env->GetObjectClass(packageManager);

    // Ambil tanda tangan melalui MessageDigest SHA-256
    jclass messageDigestClass = env->FindClass("java/security/MessageDigest");
    jmethodID getInstanceMethod = env->GetStaticMethodID(messageDigestClass, "getInstance", "(Ljava/lang/String;)Ljava/security/MessageDigest;");
    jstring sha256Str = env->NewStringUTF("SHA-256");
    jobject digest = env->CallStaticObjectMethod(messageDigestClass, getInstanceMethod, sha256Str);
    jmethodID digestMethod = env->GetMethodID(messageDigestClass, "digest", "([B)[B");

    // Android P+ (API 28+) menggunakan GET_SIGNING_CERTIFICATES (0x08000000), sebelum itu GET_SIGNATURES (0x00000040)
    jmethodID getPackageInfoMethod = env->GetMethodID(pmClass, "getPackageInfo", "(Ljava/lang/String;I)Landroid/content/pm/PackageInfo;");
    // Gunakan flags 0x08000000 | 0x00000040
    jobject packageInfo = env->CallObjectMethod(packageManager, getPackageInfoMethod, packageName, 0x08000040);

    if (packageInfo == nullptr) {
        return env->NewStringUTF("");
    }

    jclass packageInfoClass = env->GetObjectClass(packageInfo);
    jfieldID signingInfoField = env->GetFieldID(packageInfoClass, "signingInfo", "Landroid/content/pm/SigningInfo;");
    jbyteArray certBytes = nullptr;

    if (signingInfoField != nullptr) {
        jobject signingInfo = env->GetObjectField(packageInfo, signingInfoField);
        if (signingInfo != nullptr) {
            jclass signingInfoClass = env->GetObjectClass(signingInfo);
            jmethodID getApkSigningCertificateHistoryMethod = env->GetMethodID(
                signingInfoClass, "getApkSigningCertificateHistory", "()[Landroid/content/pm/Signature;");
            jobjectArray signatures = (jobjectArray)env->CallObjectMethod(signingInfo, getApkSigningCertificateHistoryMethod);
            if (signatures != nullptr && env->GetArrayLength(signatures) > 0) {
                jobject sig = env->GetObjectArrayElement(signatures, 0);
                jclass sigClass = env->GetObjectClass(sig);
                jmethodID toByteArrayMethod = env->GetMethodID(sigClass, "toByteArray", "()[B");
                certBytes = (jbyteArray)env->CallObjectMethod(sig, toByteArrayMethod);
            }
        }
    }

    if (certBytes == nullptr) {
        // Fallback untuk legacy signatures array
        jfieldID signaturesField = env->GetFieldID(packageInfoClass, "signatures", "[Landroid/content/pm/Signature;");
        if (signaturesField != nullptr) {
            jobjectArray signatures = (jobjectArray)env->GetObjectField(packageInfo, signaturesField);
            if (signatures != nullptr && env->GetArrayLength(signatures) > 0) {
                jobject sig = env->GetObjectArrayElement(signatures, 0);
                jclass sigClass = env->GetObjectClass(sig);
                jmethodID toByteArrayMethod = env->GetMethodID(sigClass, "toByteArray", "()[B");
                certBytes = (jbyteArray)env->CallObjectMethod(sig, toByteArrayMethod);
            }
        }
    }

    if (certBytes == nullptr) {
        return env->NewStringUTF("");
    }

    jbyteArray hashBytes = (jbyteArray)env->CallObjectMethod(digest, digestMethod, certBytes);
    jsize len = env->GetArrayLength(hashBytes);
    jbyte* bytes = env->GetByteArrayElements(hashBytes, nullptr);

    std::stringstream ss;
    for (int i = 0; i < len; ++i) {
        ss << std::hex << std::setw(2) << std::setfill('0') << (static_cast<int>(bytes[i]) & 0xFF);
    }
    env->ReleaseByteArrayElements(hashBytes, bytes, JNI_ABORT);

    std::string hexStr = ss.str();
    for (char &c : hexStr) {
        c = static_cast<char>(toupper(c));
    }

    return env->NewStringUTF(hexStr.c_str());
}

} // extern "C"
