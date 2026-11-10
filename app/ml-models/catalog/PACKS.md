# ReFra text recognition packs

<!-- Generated from model-catalog-v1.json by OcrCatalogCoverageTest. Do not edit by hand. -->

ReFra reads text in photos on the device with PaddlePaddle PP-OCRv5 mobile models (Apache-2.0).
Versions without internet access cannot download packs. Download the files below on another device,
copy them to the phone, then open **Settings › Smart features › Text recognition › Language packs ›
Add model files** and pick them. ReFra accepts a file only when its size and SHA-256 match this list;
file names do not matter.

Every pack needs the shared **text finder** once. Each pack adds one recognizer file.

## Text finder (needed once)

| Model | File | Size | SHA-256 | Download |
| --- | --- | --- | --- | --- |
| `pp-ocrv5-mobile-det` | `inference.onnx` | 4826518 bytes | `a431985659dc921974177a95adcfbb90fd9e51989a5e04d70d0b75f597b6e61d` | [PaddlePaddle/PP-OCRv5_mobile_det_onnx@e6f4fa85f00e](https://huggingface.co/PaddlePaddle/PP-OCRv5_mobile_det_onnx/resolve/e6f4fa85f00e168c862bc462aebca69eef9b3d3d/inference.onnx) |

## Language packs

### Latin script

Reads: Afrikaans, Albanian, Basque, Bosnian, Catalan, Croatian, Czech, Danish, Dutch, Estonian, Filipino, Finnish, French, Galician, German, Hungarian, Icelandic, Indonesian, Irish, Italian, Kurdish, Latvian, Lithuanian, Luxembourgish, Malay, Maltese, Māori, Norwegian Bokmål, Norwegian Nynorsk, Occitan, Polish, Portuguese, Quechua, Romanian, Romansh, Serbian (Latin), Slovak, Slovenian, Spanish, Swahili, Swedish, Turkish, and English.

| Model | File | Size | SHA-256 | Download |
| --- | --- | --- | --- | --- |
| `pp-ocrv5-mobile-rec-latin` | `inference.onnx` | 8042023 bytes | `7888113072263cb471b93f66dd5e2ad70548dc526fa1ace760d0d973dd121498` | [PaddlePaddle/latin_PP-OCRv5_mobile_rec_onnx@89d3a50e2c27](https://huggingface.co/PaddlePaddle/latin_PP-OCRv5_mobile_rec_onnx/resolve/89d3a50e2c27e2e7cceeab0e944c25c807d5db4f/inference.onnx) |

### English only

Reads: English.

| Model | File | Size | SHA-256 | Download |
| --- | --- | --- | --- | --- |
| `pp-ocrv5-mobile-rec-en` | `inference.onnx` | 7848423 bytes | `b5f833dfc5d0eb71da397b4efa06ebeee9b431b690a47d6af40d77d8eabc557f` | [PaddlePaddle/en_PP-OCRv5_mobile_rec_onnx@3fafbc3b5dcf](https://huggingface.co/PaddlePaddle/en_PP-OCRv5_mobile_rec_onnx/resolve/3fafbc3b5dcf93dd72add9f48368be8a3a2cd33b/inference.onnx) |

### Chinese and Japanese

Reads: Japanese, Simplified Chinese, Traditional Chinese, and English.

| Model | File | Size | SHA-256 | Download |
| --- | --- | --- | --- | --- |
| `pp-ocrv5-mobile-rec-cjk` | `inference.onnx` | 16534782 bytes | `da72dc72ca4dc220df0dfde68c1dedc31c58d3e76a25871122e5056227d50092` | [PaddlePaddle/PP-OCRv5_mobile_rec_onnx@ed152b8b495f](https://huggingface.co/PaddlePaddle/PP-OCRv5_mobile_rec_onnx/resolve/ed152b8b495f84de93cda5709d768548a9127622/inference.onnx) |

### Korean

Reads: Korean, and English.

| Model | File | Size | SHA-256 | Download |
| --- | --- | --- | --- | --- |
| `pp-ocrv5-mobile-rec-korean` | `inference.onnx` | 13418787 bytes | `92f0b7785e64fc9090106a241cf4c1eb97472824558272751b88a2a4476d3a08` | [PaddlePaddle/korean_PP-OCRv5_mobile_rec_onnx@5c6f574b8e22](https://huggingface.co/PaddlePaddle/korean_PP-OCRv5_mobile_rec_onnx/resolve/5c6f574b8e2230adf4287b33e736d71b9fabd28e/inference.onnx) |

### Cyrillic script · Belarusian, Russian, Ukrainian

Reads: Belarusian, Russian, Ukrainian, and English.

| Model | File | Size | SHA-256 | Download |
| --- | --- | --- | --- | --- |
| `pp-ocrv5-mobile-rec-eslav` | `inference.onnx` | 7887627 bytes | `b3018ef2b09a0250b6e0c8e871c927098363e5fd4df890cc68e8358eb0aaf1bd` | [PaddlePaddle/eslav_PP-OCRv5_mobile_rec_onnx@9a32171fc571](https://huggingface.co/PaddlePaddle/eslav_PP-OCRv5_mobile_rec_onnx/resolve/9a32171fc5718746875e1a261818884517975013/inference.onnx) |

### Cyrillic script

Reads: Bashkir, Belarusian, Bulgarian, Chechen, Chuvash, Kazakh, Kyrgyz, Macedonian, Mongolian, Russian, Serbian, Tajik, Tatar, Tuvinian, Ukrainian, and English.

| Model | File | Size | SHA-256 | Download |
| --- | --- | --- | --- | --- |
| `pp-ocrv5-mobile-rec-cyrillic` | `inference.onnx` | 8048799 bytes | `5371ee1ddaa7983cc62d0818d99e982b6804638c85e4f960d59a574094e172e5` | [PaddlePaddle/cyrillic_PP-OCRv5_mobile_rec_onnx@2cef88145434](https://huggingface.co/PaddlePaddle/cyrillic_PP-OCRv5_mobile_rec_onnx/resolve/2cef88145434beb8afa9dd82d77d799eb1ad7b29/inference.onnx) |

### Arabic script

Reads: Arabic, Central Kurdish, Pashto, Persian, Sindhi, Urdu, Uyghur, and English.

| Model | File | Size | SHA-256 | Download |
| --- | --- | --- | --- | --- |
| `pp-ocrv5-mobile-rec-arabic` | `inference.onnx` | 7998947 bytes | `799113ebf267fbe742deb99eb36e8d42c9ddc5291ceacf92add41b4d52a59110` | [PaddlePaddle/arabic_PP-OCRv5_mobile_rec_onnx@14aaedcd7582](https://huggingface.co/PaddlePaddle/arabic_PP-OCRv5_mobile_rec_onnx/resolve/14aaedcd75825982689ecf5cd64ab33ee083215a/inference.onnx) |

### Devanagari script

Reads: Bhojpuri, Hindi, Konkani, Maithili, Marathi, Nepali, Sanskrit, and English.

| Model | File | Size | SHA-256 | Download |
| --- | --- | --- | --- | --- |
| `pp-ocrv5-mobile-rec-devanagari` | `inference.onnx` | 7912311 bytes | `cb789212ce96c69d3e74728ae4309d179281d68cb3945d0616b67cafab41c986` | [PaddlePaddle/devanagari_PP-OCRv5_mobile_rec_onnx@251aec19e367](https://huggingface.co/PaddlePaddle/devanagari_PP-OCRv5_mobile_rec_onnx/resolve/251aec19e36739540d35e2cc943f6aa7503b98e5/inference.onnx) |

### Greek script

Reads: Greek, and English.

| Model | File | Size | SHA-256 | Download |
| --- | --- | --- | --- | --- |
| `pp-ocrv5-mobile-rec-greek` | `inference.onnx` | 7808735 bytes | `2acf17fcaea2bc81b878e311e6263b8885f48bb03796f75f9f30ed3242bbaa6d` | [PaddlePaddle/el_PP-OCRv5_mobile_rec_onnx@8152b89d2ee0](https://huggingface.co/PaddlePaddle/el_PP-OCRv5_mobile_rec_onnx/resolve/8152b89d2ee0e1d4c92decab75fe75e5e3d836b6/inference.onnx) |

### Thai script

Reads: Thai, and English.

| Model | File | Size | SHA-256 | Download |
| --- | --- | --- | --- | --- |
| `pp-ocrv5-mobile-rec-thai` | `inference.onnx` | 7891015 bytes | `27618be66018f8598ac0a526a593f9f1cebf794e7eded93428e8fb016e537f5f` | [PaddlePaddle/th_PP-OCRv5_mobile_rec_onnx@1d4adbbafb10](https://huggingface.co/PaddlePaddle/th_PP-OCRv5_mobile_rec_onnx/resolve/1d4adbbafb1034a2fd6618498575b81ea7b69f69/inference.onnx) |

### Tamil script

Reads: Tamil, and English.

| Model | File | Size | SHA-256 | Download |
| --- | --- | --- | --- | --- |
| `pp-ocrv5-mobile-rec-tamil` | `inference.onnx` | 7885691 bytes | `c6d2b682d2a0ea4cb1fccdba295976f93fd439964d16cdc666cadef531accbee` | [PaddlePaddle/ta_PP-OCRv5_mobile_rec_onnx@0db89798af0d](https://huggingface.co/PaddlePaddle/ta_PP-OCRv5_mobile_rec_onnx/resolve/0db89798af0d5218fad9ea7ace0557526e9077f1/inference.onnx) |

### Telugu script

Reads: Telugu, and English.

| Model | File | Size | SHA-256 | Download |
| --- | --- | --- | --- | --- |
| `pp-ocrv5-mobile-rec-telugu` | `inference.onnx` | 7898759 bytes | `8238bfc46d4cffe720ed6706e3842802467343497428693ff2bfb4e6b3caa36b` | [PaddlePaddle/te_PP-OCRv5_mobile_rec_onnx@f1197c834302](https://huggingface.co/PaddlePaddle/te_PP-OCRv5_mobile_rec_onnx/resolve/f1197c83430241c58c64b0952b3a80366d04eeba/inference.onnx) |

## Not available yet

No on-device model reads Bangla, Hebrew, Odia, Punjabi, Vietnamese yet.
ReFra does not substitute another pack for them.
