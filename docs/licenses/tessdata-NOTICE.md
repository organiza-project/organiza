# Tesseract language data

`app/android/src/main/assets/tessdata/eng.traineddata` and `ara.traineddata` are the English and
Arabic models from [tesseract-ocr/tessdata_fast](https://github.com/tesseract-ocr/tessdata_fast),
licensed under the Apache License 2.0. They are used only on the phone, to read timetable photos
(ADR-0018). Every bundled model is read together, as `eng+ara`.

SHA-256:

- `eng.traineddata`: `7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2`
- `ara.traineddata`: `e3206d3dc87fd50c24a0fb9f01838615911d25168f4e64415244b67d2bb3e729`
