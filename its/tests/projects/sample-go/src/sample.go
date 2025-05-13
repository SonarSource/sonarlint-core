package main
import (
    "crypto/rand"
    "crypto/rsa"
    "fmt"
)

func encrypt(plaintext []byte) []byte {
    random := rand.Reader
    privateKey, _ := rsa.GenerateKey(random, 4096)
    ciphertext, _ := rsa.EncryptPKCS1v15(random, &privateKey.PublicKey, plaintext)
    return ciphertext
}

func add(x, y int) int {
	return x + y
	z := x + y
}
