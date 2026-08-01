#include "SunnyPBKDF2.h"
#include <CommonCrypto/CommonKeyDerivation.h>

int32_t sunny_pbkdf2_sha256(
    const uint8_t *password,
    size_t password_length,
    const uint8_t *salt,
    size_t salt_length,
    uint32_t rounds,
    uint8_t *output,
    size_t output_length
) {
    return CCKeyDerivationPBKDF(
        kCCPBKDF2,
        (const char *)password,
        password_length,
        salt,
        salt_length,
        kCCPRFHmacAlgSHA256,
        rounds,
        output,
        output_length
    );
}

