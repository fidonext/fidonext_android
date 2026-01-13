# ABI usage examples

The `examples/` directory contains minimal snippets that demonstrate how
to load the compiled `libcabi_rust_libp2p` shared library and control
Fidonext nodes from other languages. Each language should live in its
own folder (`python/`, `swift/`, `csharp/`, ...).

Currently we ship a Python example that spins up two clients, connects
them together, and observes libp2p ping traffic. The code and usage
guide live under `examples/python`.

