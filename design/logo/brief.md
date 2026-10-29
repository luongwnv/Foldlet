# LIT Screen OS — brief thiết kế lại logo

Ứng dụng mở menu và điều hướng app trên màn hình ngoài Galaxy Z Flip3, dùng cùng Samsung. Người quyết định: chủ dự án trong cuộc trò chuyện.

Giả định: gọn, hiện đại, cân đối, dễ nhận ở 16–32 px. Giữ ý tưởng mở rộng không gian màn hình; bỏ hình điện thoại minh họa quá nhiều chi tiết. Không có chữ L trong biểu tượng, không dùng robot Android hoặc tài nguyên thương hiệu CoverScreen OS. Màu xanh của Settings là hướng phối màu sau khi chọn hình.

Loại mark ưu tiên: biểu tượng nhỏ trước, tên ứng dụng đặt ngoài biểu tượng. Dùng ở launcher Android, Settings, splash và thông báo. Thành công: một màu vẫn đọc được, không mất khe ở 16 px, không giống một nút chức năng thông thường.

## Quan sát và hướng đi

Đã đối chiếu CoverScreen OS cài trên máy và thư viện logo-design: os-platform có 46 mẫu, thiên về vòng tròn, xanh và hình trừu tượng. Tránh robot, bốn ô Windows, tấm điện thoại có camera li ti, ngọn lửa chữ “lit” và hiệu ứng bóng/gradient làm ý tưởng chính.

Word map: cover → giới hạn/khung → khe mở; flip → khớp/nếp/đối xứng; launcher → bước ra/cửa/điểm kích hoạt; small → nén/chắc/rõ. Các giao điểm được chọn: nếp đối nhau, cửa mở, ô vuông bước ra khỏi vòng giới hạn.

| Ý tưởng | Loại | Điểm /25 | Quyết định |
|---|---|---:|---|
| Hai nếp đối nhau, khe chạy xuyên khối | Trừu tượng | 23 | Dựng A |
| Khung cửa mở, một cánh đặc tạo chiều sâu | Pictorial | 22 | Dựng B |
| Ô vuông mở ra ngoài một vòng giới hạn | Emblem | 21 | Dựng C |
| Lưới bốn ô với một ô nâng lên | Trừu tượng | 16 | Quá gần ngôn ngữ Windows |
| Hai thẻ màn hình chồng lên nhau | Pictorial | 17 | Dễ thành icon đa nhiệm chung |
| Chữ L ẩn trong mặt gập | Letterform | 5 | Không hợp yêu cầu bỏ L |
| Một ngọn sáng nhỏ thoát ra khỏi khung | Pictorial | 15 | Dễ bị đọc là app đèn pin |
| Vệt sóng quanh một màn hình | Trừu tượng | 14 | Gần biểu tượng truyền màn hình |
| Wordmark LIT Screen OS hình học | Wordmark | 16 | Tên dài, không phù hợp icon 16 px |
| Một viên gạch xoay 90° thành khung mở | Trừu tượng | 18 | Giữ làm phương án dự phòng |

Điểm dựa trên ý tưởng, độ khác biệt, đơn giản, liên quan và đọc nhỏ. Thư viện được dùng để nghiên cứu hình học, không sao chép. A cần tránh bị hiểu là chữ S; B cần tránh cảm giác app cửa/nhà; C trừu tượng hơn và cần thời gian nhận diện.

## Dựng hình

- A: hai khối trên lưới 8 đơn vị, góc vát 45°, đối xứng quay 180°. Dùng khoảng âm giữa hai nếp làm điểm nhận diện.
- B: một khung mở với cung tròn bán kính 32/8 và cánh nghiêng 15°, không camera, chữ hoặc tay nắm nhỏ.
- C: vòng tròn mở một góc tư và ô vuông tách rời ở góc mở; khoảng trống lớn để đọc ở 16 px.

Mỗi bản dùng viewBox 256 × 256, màu đen, hình tô kín, không text/raster/filter/mask trong biểu tượng. Lưu v1/v2 để so và kiểm tra trước khi trình ba phương án. Bộ logo hoàn chỉnh và việc thay icon app thực hiện sau khi người dùng chọn hướng theo checkpoint của skill.
