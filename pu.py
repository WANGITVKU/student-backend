from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives import hashes, hmac
import os

# Giả định Password Element (PE) là một điểm trên elliptic curve P-256
curve = ec.SECP256R1()
PE = ec.generate_private_key(curve).public_key()

# Client chọn số ngẫu nhiên a
a = ec.generate_private_key(curve)
commit_client = a.public_key()

# AP chọn số ngẫu nhiên b
b = ec.generate_private_key(curve)
commit_ap = b.public_key()

# Tính khóa phiên (cả hai bên đều tính ab*PE)
shared_client = a.exchange(ec.ECDH(), commit_ap)
shared_ap = b.exchange(ec.ECDH(), commit_client)

print("Khóa phiên phía Client:", shared_client.hex())
print("Khóa phiên phía AP    :", shared_ap.hex())

# Confirm Exchange: tạo MAC để chứng minh có cùng khóa phiên
def make_confirm(key, message):
    h = hmac.HMAC(key, hashes.SHA256())
    h.update(message)
    return h.finalize()

message = b"Dragonfly handshake data"
confirm_client = make_confirm(shared_client, message)
confirm_ap = make_confirm(shared_ap, message)

print("Confirm Client:", confirm_client.hex())
print("Confirm AP    :", confirm_ap.hex())

if confirm_client == confirm_ap:
    print("Xác thực thành công: cả hai bên có cùng khóa phiên.")
else:
    print("Xác thực thất bại.")
