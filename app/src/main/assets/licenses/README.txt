Modelli di terzi inclusi in Zesty (eseguiti sul telefono, senza rete):

1. IS-Net general-use (models/isnet_general.onnx)
   X. Qin et al., "Highly Accurate Dichotomous Image Segmentation", ECCV 2022
   Pesi originali: https://github.com/xuebinqin/DIS — Apache License 2.0
   Esportazione ONNX: imgly (MIT, vedi isnet-onnx-imgly-MIT.txt); quantizzazione int8 dei soli pesi: huggingface.co/xrds/isnet-general-onnx-int8
   SHA-256 3b21a6706dc8d6e4ba9f5b31ebc6940f6c785b58862e27bb25daa9dd4424b87f

2. MODNet (models/modnet_portrait.onnx)
   Z. Ke et al., "MODNet: Real-Time Trimap-Free Portrait Matting via Objective Decomposition", AAAI 2022
   https://github.com/ZHKKKe/MODNet — Apache License 2.0
   Esportazione ONNX quantizzata: huggingface.co/Xenova/modnet (Apache 2.0)
   SHA-256 92e49898c3e05a6d7a944fc67a8cb87c4aad754ffb6ebd949528c7d1105fee3a

3. ONNX Runtime (com.microsoft.onnxruntime:onnxruntime-android) — Microsoft, MIT License
