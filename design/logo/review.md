# Kiểm tra concept — hai vòng

Vòng 1: dựng ba biểu tượng một màu, audit SVG và render bảng 64/32/16 px. Đã xem ảnh `concepts-v1.png`. A có khe 24 đơn vị hơi chật ở 16 px; B có một mảnh âm nhỏ không chủ ý ở chân cánh cửa; C có thể tăng trọng lượng vòng để đọc nhỏ chắc hơn.

Vòng 2: A tăng khe lên 32 đơn vị; B nối chân cánh vào khung để bỏ mảnh âm; C tăng độ dày vòng từ 32 lên 36 đơn vị và đưa ô vuông lên 8 đơn vị, giảm bán kính góc 16 xuống 12. Audit v2 không báo vấn đề cấu trúc. Đã render và xem bảng `concepts-v2.png`, trang thử kích thước/nền/đơn sắc, và bảng so với Framer, Pagekit, Buildkite cùng icon hiện tại để nghiên cứu, không sao chép.

Các điểm audit là heuristic, không phải chứng nhận nhãn hiệu. Không thực hiện tra cứu đăng ký nhãn hiệu. SVG biểu tượng không có text, raster, mask, filter, gradient hay stroke phụ thuộc công cụ.

Đề xuất A: gọn, nét khỏe và liên hệ với nếp gập mà không vẽ một chiếc điện thoại. B trực quan hơn nhưng dễ bị đọc thành chữ C/cửa; C mềm và rõ nhỏ nhưng trừu tượng hơn. Người dùng đã chọn **A — Nếp mở**; checkpoint hoàn tất.

## Bộ chính thức

Giữ nguyên hình A v2, bổ sung bản teal, đen, trắng và cyan; wordmark hình học riêng bằng path, logo ngang/dọc và icon. Đã xem các bản render, bảng ứng dụng và thử hình ở 16/32/48/64 px, trắng trên nền tối, đen một màu, mặt nạ tròn/vuông/góc bo. Ở 16 px các cạnh ngang/dọc khớp pixel, khe mở 2 px; đường vát vẫn khử răng cưa bình thường. Không cần đổi hình hoặc làm mảnh bản trắng. Bản chữ thu nhỏ quá mức không đọc được nên quy định kích thước tối thiểu trong hướng dẫn.

Theo phản hồi mới, nền icon chuyển từ navy sang xanh nhạt `#D8F2FC`, hình teal `#006F95`. Bản cyan/trắng vẫn là lựa chọn cho các ngữ cảnh thương hiệu nền tối, không dùng làm nền icon Android. Bộ icon chức năng Settings giữ nét viền.

Audit `audit-final.txt` không báo lỗi cấu trúc; các bản có wordmark có thông tin số điểm cao hơn median của biểu tượng đơn, do gồm đủ tên ứng dụng. Kiểm tra build/lint và smoke Settings 0.5.1 đã qua; hình thực tế trong tab Thông tin và launcher được kiểm tra trên thiết bị/máy ảo. Khối thương hiệu đầu Tổng quan đã được bỏ theo yêu cầu riêng.
