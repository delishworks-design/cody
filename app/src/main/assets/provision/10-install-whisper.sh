#!/data/data/com.termux/files/usr/bin/bash
# Step 10: install Whisper for on-device dictation.
#
# Android's built-in recogniser proved too weak for dictating code requests, so the mic records raw
# audio and this transcribes it inside the container instead.
#
# whisper.cpp rather than faster-whisper or whisper.cpp's Python bindings: it is a single static
# binary with no torch, no CUDA and no Python, which matters a great deal inside proot on a phone.
# The `base` model is the sweet spot here - `tiny` is noticeably worse on technical vocabulary, and
# anything larger starts to take long enough to be annoying to wait for on a mobile CPU.
#
# Optional step: a failure here must not fail provisioning, so the script always exits 0. The app
# falls back to the platform recogniser when this is absent.
PREFIX=/data/data/com.termux/files/usr
HOME_DIR=/data/data/com.termux/files/home
PATH=$PREFIX/bin
TMPDIR=$PREFIX/tmp
export PREFIX HOME="$HOME_DIR" PATH TMPDIR LANG=en_US.UTF-8
cd "$HOME_DIR" || exit 1

WHISPER_DIR=/root/.whisper

proot-distro login debian --shared-tmp -- /bin/bash -lc '
    set -u
    export DEBIAN_FRONTEND=noninteractive
    export SSL_CERT_FILE=/etc/ssl/certs/ca-certificates.crt

    # Defined HERE, not in the outer script. `proot-distro login` does not pass the environment
    # through to the container at all — an exported variable on the Termux side never arrives, and
    # `set -u` then turns the missing $MODEL into a fatal error. That killed this step immediately
    # after a *successful* compile, before the model was fetched or run.sh was written, while the
    # step still reported itself done because it always exits 0.
    MODEL=base

    mkdir -p /root/.whisper
    cd /root/.whisper || exit 0

    # Already installed? Then only make sure the model is present.
    if [ ! -x /usr/local/bin/whisper-cli ] && [ ! -x /root/.whisper/main ]; then
        apt-get update -y >/dev/null 2>&1
        apt-get install -y build-essential cmake pkg-config wget ca-certificates >/dev/null 2>&1

        # Say what is missing instead of failing silently further down. Every command in this block
        # discards its output, so without these checks a missing `wget` looks exactly like a network
        # hiccup and a missing `cmake` looks like a broken download -- and the step still reports
        # success, because it always exits 0.
        for tool in cmake wget; do
            command -v $tool >/dev/null 2>&1 || { echo "WHISPER_MISSING_$tool"; exit 0; }
        done

        for url in \
            https://github.com/ggerganov/whisper.cpp/archive/refs/heads/master.tar.gz \
            https://codeload.github.com/ggerganov/whisper.cpp/tar.gz/refs/heads/master
        do
            if wget -q --timeout=60 -O src.tar.gz "$url" 2>/dev/null; then
                tar xzf src.tar.gz 2>/dev/null && break
            fi
        done
        d=$(find . -maxdepth 1 -type d -name "whisper.cpp-*" | head -n1)
        if [ -n "$d" ]; then
            cd "$d" || exit 0
            # Parallelism is capped rather than set to nproc. An 8-core phone reports 8, but proot plus the
            # running opencode server plus the rest of the environment can leave under 3GB free, and a
            # -j8 compile is the most likely thing here to get OOM-killed. Half the cores builds this
            # small C project only modestly slower and does not risk taking the session down with it.
            JOBS=$(( $(nproc) / 2 ))
            [ "$JOBS" -lt 1 ] && JOBS=1

            cmake -B build -DCMAKE_BUILD_TYPE=Release \
                  -DWHISPER_BUILD_TESTS=OFF -DWHISPER_BUILD_EXAMPLES=ON >/dev/null 2>&1
            cmake --build build --config Release -j"$JOBS" --target whisper-cli >/dev/null 2>&1
            cp -f build/bin/whisper-cli /usr/local/bin/whisper-cli 2>/dev/null
            chmod +x /usr/local/bin/whisper-cli 2>/dev/null
            cd /root/.whisper || exit 0
        fi
    fi

    if [ ! -x /usr/local/bin/whisper-cli ] && [ ! -x /root/.whisper/main ]; then
        # Tests the paths, not PATH resolution. `command -v whisper-cli` fails here even when the
        # binary is sitting in /usr/local/bin, because `proot-distro login ... /bin/bash -lc` is not
        # guaranteed to put that directory on PATH — and that made this script exit straight after a
        # successful compile, before writing run.sh or fetching the model, while still reporting
        # that the step was done.
        echo "WHISPER_BINARY_MISSING"
        exit 0
    fi

    # The model is a separate download from the binary; fetch it if it is not already cached.
    if [ ! -f "/root/.whisper/ggml-$MODEL.bin" ]; then
        wget -q --timeout=300 -O "/root/.whisper/ggml-$MODEL.bin.part" \
            "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-$MODEL.bin" 2>/dev/null
        if [ -s "/root/.whisper/ggml-$MODEL.bin.part" ]; then
            mv -f "/root/.whisper/ggml-$MODEL.bin.part" "/root/.whisper/ggml-$MODEL.bin"
        else
            rm -f "/root/.whisper/ggml-$MODEL.bin.part"
            echo "WHISPER_MODEL_DOWNLOAD_FAILED"
            exit 0
        fi
    fi

    # Stable entry point: the app never has to know where cmake put the binary.
    #
    # The binary is invoked by absolute path, not by bare name. The app runs this script through
    # `proot-distro login debian -- /root/.whisper/run.sh`, which is *not* a login shell, so
    # /usr/local/bin is not guaranteed to be on PATH there — and a missing PATH entry would fail as
    # "command not found" only at dictation time, long after setup reported success. Both locations
    # cmake may have chosen are tried explicitly.
    cat > /root/.whisper/run.sh <<RUNEOF
#!/bin/bash
BIN=/usr/local/bin/whisper-cli
[ -x "\$BIN" ] || BIN=/root/.whisper/main
[ -x "\$BIN" ] || { echo "WHISPER_BINARY_MISSING" >&2; exit 127; }
exec "\$BIN" -m /root/.whisper/ggml-$MODEL.bin -l en -nt -np -t "\${WHISPER_THREADS:-4}" "\$@"
RUNEOF
    chmod +x /root/.whisper/run.sh

    # Prove the entry point actually works before declaring success, rather than assuming that
    # writing it was enough. A missing shared library only shows up at exec time on Android.
    if /root/.whisper/run.sh --help >/dev/null 2>&1; then
        echo "WHISPER_READY"
    else
        echo "WHISPER_ENTRYPOINT_FAILED"
    fi
'

echo "WHISPER_STEP_DONE"
exit 0
