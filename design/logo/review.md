# Kiểm tra concept — hai vòng

Vòng 1: dựng ba biểu tượng một màu, audit SVG và render bảng 64/32/16 px. Đã xem ảnh `concepts-v1.png`. A có khe 24 đơn vị hơi chật ở 16 px; B có một mảnh âm nhỏ không chủ ý ở chân cánh cửa; C có thể tăng trọng lượng vòng để đọc nhỏ chắc hơn.

Vòng 2: A tăng khe lên 32 đơn vị; B nối chân cánh vào khung để bỏ mảnh âm; C tăng độ dày vòng từ 32 lên 36 đơn vị và đưa ô vuông lên 8 đơn vị, giảm bán kính góc 16 xuống 12. Audit v2 không báo vấn đề cấu trúc. Đã render và xem bảng `concepts-v2.png`, trang thử kích thước/nền/đơn sắc, và bảng so với Framer, Pagekit, Buildkite cùng icon hiện tại để nghiên cứu, không sao chép.

Các điểm audit là heuristic, không phải chứng nhận nhãn hiệu. Không thực hiện tra cứu đăng ký nhãn hiệu. SVG biểu tượng không có text, raster, mask, filter, gradient hay stroke phụ thuộc công cụ. A/B/C là phương án chưa phê duyệt, chưa có bộ màu/lockup/kit hoàn chỉnh và chưa thay icon Android.

Đề xuất A: gọn, nét khỏe và liên hệ với nếp gập mà không vẽ một chiếc điện thoại. B trực quan hơn nhưng dễ bị đọc thành chữ C/cửa; C mềm và rõ nhỏ nhưng trừu tượng hơn. Chờ người dùng chọn trước khi xuất bộ logo và áp dụng theo checkpoint của skill logo-design.
