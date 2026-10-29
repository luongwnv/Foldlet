# LIT Screen OS · Nếp mở

Hướng A v2 được chủ dự án chọn ngày 29/10/2026. Hai nếp đối xứng quay 180° gợi chuyển động gập và mở rộng không gian. Không có chữ L đặt trong biểu tượng. Hình đã chọn được giữ nguyên khi triển khai màu và Android.

## Chọn file

| Nhu cầu | File |
| --- | --- |
| Biểu tượng nền sáng | `final/symbol-brand.svg` |
| Nền tối | `final/symbol-cyan.svg` hoặc `symbol-white.svg` |
| In một màu | `final/symbol-black.svg` / `symbol-white.svg` |
| PDF vector RGB | `exports/symbol-brand.pdf`, `horizontal-brand.pdf` |
| Logo và tên | `final/horizontal-*.svg` hoặc `stacked-*.svg` |
| Chỉ tên | `final/wordmark-*.svg` |
| Icon app, ảnh chia sẻ | `final/app-tile.svg`, PNG trong `exports/` |
| Web | `exports/favicon.svg`, `.ico`, PNG 16/32/48, Apple 180, 192/512, maskable 512 |
| Android | `app/src/main/res/drawable/ic_launcher_foreground.xml`, `ic_launcher_monochrome.xml`, `ic_status.xml` và mipmap adaptive hiện có |

`final/` là bản vector gốc; tất cả chữ trong logo đều là đường path. PNG xuất nền trong suốt, trừ các ô icon có nền xanh nhạt. Người dùng yêu cầu bỏ nền tối khỏi icon app; bản Android và mọi ô icon xuất sẵn đều dùng nền sáng. Bản 1024 trong tên file là cạnh dài nhất; logo ngang/dọc giữ đúng tỷ lệ. `presentation.html` là bảng minh họa ứng dụng, không phải giao diện hoặc website được phát hành.

## Khoảng trống và kích thước

Lưới biểu tượng 256 × 256; hình từ 32 đến 224, nét dày 48, khe nhỏ nhất 32. Giữ khoảng trống tối thiểu **32 đơn vị**, tính từ mép hình, ở cả bốn phía. Không đặt chữ, badge hoặc chi tiết lên khe âm.

Biểu tượng độc lập tối thiểu **16 × 16 px**, dùng `symbol-small.svg` hoặc favicon 16 đã xuất. Hình dùng cùng đường nét gốc vì ở 16 px nét đạt 3 px, khe đạt 2 px và các điểm đều khớp pixel. Từ 32 px trở lên dùng bản thường. Logo ngang tối thiểu rộng **240 px**, logo dọc **160 px**, tên riêng rộng **160 px** để dòng SCREEN OS còn đọc được. Dưới các cỡ này dùng biểu tượng.

Android foreground có canvas 108 dp, vùng mặt nạ tham chiếu 72 dp. Nhóm path scale 0,6 quanh tâm khiến hình rộng 48,6 dp; các góc nằm trong vùng an toàn hình tròn. Ô icon xuất sẵn có hình rộng 67,5% cạnh. Android tự áp mặt nạ: không thêm góc bo hoặc nền vào foreground. Monochrome dùng cùng tỷ lệ foreground; status dùng canvas 24 dp riêng để hình đọc rõ ở thanh trạng thái. Cả hai dùng path trắng, hệ thống tự tô theo ngữ cảnh. Không dùng màu nhiều tông cho icon thông báo.

## Màu

| Vai trò | HEX / RGB | CMYK gần đúng* |
| --- | --- | --- |
| Teal, nền sáng | `#006F95` · 0, 111, 149 | 100, 26, 0, 42 |
| Xanh nhạt, nền icon | `#D8F2FC` · 216, 242, 252 | 14, 4, 0, 1 |
| Navy, nền tối tùy chọn | `#102437` · 16, 36, 55 | 71, 35, 0, 78 |
| Cyan, trên navy | `#7DDCEA` · 125, 220, 234 | 47, 6, 0, 8 |
| Đen / trắng | `#000000` / `#FFFFFF` | 0, 0, 0, 100 / 0, 0, 0, 0 |

*Bộ chính dùng RGB cho màn hình. CMYK chỉ là quy đổi số tham khảo, chưa proof theo giấy/profile và chưa chỉ định Pantone. Dùng trắng trên nền tối hoặc đen trên nền sáng khi không dùng màu thương hiệu. Không dùng cyan trên nền trắng cho logo nhỏ.

Không kéo giãn, xoay thêm, đổi khoảng cách hai nếp, thêm gradient/bóng, viền quanh hình hoặc đặt chữ L vào tâm. Giữ độ dày ở bản trắng: không cần bản đảo màu mảnh hơn vì khoảng âm đã rộng. Không biến icon thao tác Cài đặt thành biểu tượng đặc; bộ nét viền của chúng có vai trò riêng.

## Chữ và nguồn

Wordmark LIT / SCREEN OS là chữ hình học được dựng trực tiếp trong `generate.py`, không dùng font nhúng hoặc font chuyển đổi của bên thứ ba. Chỉ dùng các glyph này cho tên thương hiệu, không dùng thay font nội dung. Giao diện Android tiếp tục dùng system `sans` theo thiết bị, không phân phối file font.

Hình và chữ được dựng riêng cho dự án. Các logo thư viện chỉ dùng so sánh hình học; không đưa tài nguyên thương hiệu CoverScreen OS vào kit. Kiểm tra thị giác và audit SVG không thay thế tra cứu đăng ký nhãn hiệu; chưa thực hiện tra cứu đăng ký.

## Tái tạo và kiểm tra

Chạy `python3 design/logo/generate.py` từ repo để dựng các master SVG và ba vector Android. PNG và web icon được xuất từ master bằng `logo-design/scripts/export_variants.py` / `render_png.py`; favicon 16/32/48 dùng trực tiếp `symbol-small.svg` để giữ lưới.

Đã kiểm tra bảng một màu/đảo màu, 16/32/64 px, mặt nạ tròn/vuông, góc bo và các lockup. Xem `review.md`, `audit-final.txt` và bảng trình bày. Các điểm audit là heuristic về cấu trúc SVG, không phải chứng nhận chất lượng hoặc quyền sở hữu.
