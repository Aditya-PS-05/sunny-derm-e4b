#ifndef SunnyPBKDF2_h
#define SunnyPBKDF2_h

#include <stddef.h>
#include <stdint.h>

int32_t sunny_pbkdf2_sha256(
    const uint8_t *password,
    size_t password_length,
    const uint8_t *salt,
    size_t salt_length,
    uint32_t rounds,
    uint8_t *output,
    size_t output_length
);

#endif

