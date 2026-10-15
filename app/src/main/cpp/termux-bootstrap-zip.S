    .global blob
    .global blob_size
    .section .rodata
 blob:
 #if defined __x86_64__
     .incbin "bootstrap-x86_64.zip"
 #elif defined __aarch64__
     .incbin "bootstrap-aarch64.zip"
 #else
 # error Unsupported architecture
 #endif
 1:
 blob_size:
     .int 1b - blob
