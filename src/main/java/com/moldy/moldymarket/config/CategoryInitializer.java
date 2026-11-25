package com.moldy.moldymarket.config;

import com.moldy.moldymarket.category.entity.Category;
import com.moldy.moldymarket.category.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@Order(3)
@RequiredArgsConstructor
public class CategoryInitializer implements CommandLineRunner {

    private final CategoryRepository categoryRepository;

    private record CategorySeed(String name, String description, List<String> subCategories) {}

    private static final List<CategorySeed> SEEDS = List.of(
            new CategorySeed(
                    "Đồ điện tử",
                    "Điện thoại, máy tính, máy ảnh, thiết bị âm thanh và linh kiện công nghệ đã qua sử dụng.",
                    List.of(
                            "Điện thoại",
                            "Laptop",
                            "Máy tính bảng",
                            "Tai nghe",
                            "Phụ kiện điện tử",
                            "Máy ảnh & Ống kính",
                            "Máy chơi game & Console",
                            "Màn hình & Linh kiện PC",
                            "Đồng hồ thông minh"
                    )
            ),
            new CategorySeed(
                    "Thời trang",
                    "Quần áo, giày dép, túi xách và phụ kiện thời trang secondhand tuyển chọn.",
                    List.of(
                            "Áo",
                            "Quần",
                            "Giày",
                            "Túi xách",
                            "Phụ kiện",
                            "Đồng hồ & Trang sức",
                            "Kính mắt"
                    )
            ),
            new CategorySeed(
                    "Đồ nội thất",
                    "Nội thất phòng khách, phòng ngủ, bàn làm việc và đồ trang trí nhà cửa.",
                    List.of(
                            "Bàn",
                            "Ghế",
                            "Tủ",
                            "Giường",
                            "Đèn",
                            "Kệ & Giá sách",
                            "Đồ trang trí nhà cửa"
                    )
            ),
            new CategorySeed(
                    "Sách",
                    "Sách cũ, truyện tranh, giáo trình và tài liệu học tập đa lĩnh vực.",
                    List.of(
                            "Văn học",
                            "Kinh tế",
                            "Khoa học",
                            "Công nghệ",
                            "Giáo dục",
                            "Truyện tranh & Manga",
                            "Ngoại ngữ",
                            "Kỹ năng sống"
                    )
            ),
            new CategorySeed(
                    "Đồ gia dụng & Nhà bếp",
                    "Thiết bị nhà bếp, máy pha cà phê, máy hút bụi và đồ dùng gia đình tiện ích.",
                    List.of(
                            "Nồi & Chảo",
                            "Máy pha cà phê",
                            "Máy hút bụi",
                            "Quạt & Máy lọc không khí",
                            "Thiết bị nhà bếp nhỏ"
                    )
            ),
            new CategorySeed(
                    "Xe cộ & Phụ kiện",
                    "Xe đạp, xe máy cũ, mũ bảo hiểm và phụ kiện di chuyển hằng ngày.",
                    List.of(
                            "Xe đạp",
                            "Xe máy cũ",
                            "Mũ bảo hiểm & Đồ bảo hộ",
                            "Phụ kiện xe máy",
                            "Phụ kiện ô tô"
                    )
            ),
            new CategorySeed(
                    "Thể thao & Dã ngoại",
                    "Dụng cụ tập luyện, vợt cầu lông, đồ cắm trại và trang phục thể thao.",
                    List.of(
                            "Dụng cụ gym tại nhà",
                            "Vợt cầu lông & Tennis",
                            "Đồ cắm trại & Trekking",
                            "Giày & Trang phục thể thao",
                            "Bóng & Phụ kiện thể thao"
                    )
            ),
            new CategorySeed(
                    "Nhạc cụ & Âm thanh",
                    "Đàn guitar, piano, loa, ampli và thiết bị thu âm cho người yêu nhạc.",
                    List.of(
                            "Đàn Guitar",
                            "Đàn Piano & Organ",
                            "Loa & Amply",
                            "Micro & Thiết bị thu âm",
                            "Phụ kiện nhạc cụ"
                    )
            ),
            new CategorySeed(
                    "Đồ chơi & Sưu tầm",
                    "Mô hình, Lego, board game và các món đồ cổ, đồ vintage độc đáo.",
                    List.of(
                            "Mô hình & Figure",
                            "Lego & Lắp ráp",
                            "Board game",
                            "Đồ cổ & Vintage",
                            "Thẻ bài & Kỷ vật"
                    )
            ),
            new CategorySeed(
                    "Mẹ & Bé",
                    "Xe đẩy, đồ chơi, quần áo và vật dụng chăm sóc bé tiết kiệm, an toàn.",
                    List.of(
                            "Xe đẩy & Ghế ngồi ô tô",
                            "Đồ chơi trẻ em",
                            "Quần áo trẻ em",
                            "Đồ dùng ăn dặm"
                    )
            ),
            new CategorySeed(
                    "Làm đẹp & Chăm sóc cá nhân",
                    "Nước hoa, máy tạo kiểu tóc, dụng cụ chăm sóc da và phụ kiện làm đẹp.",
                    List.of(
                            "Nước hoa",
                            "Máy sấy & Tạo kiểu tóc",
                            "Dụng cụ chăm sóc da",
                            "Túi & Hộp mỹ phẩm"
                    )
            ),
            new CategorySeed(
                    "Thú cưng",
                    "Lồng chuồng, đồ chơi, phụ kiện dắt và dụng cụ chăm sóc chó mèo.",
                    List.of(
                            "Lồng & Chuồng nuôi",
                            "Đồ chơi thú cưng",
                            "Phụ kiện dắt & Vận chuyển",
                            "Bát ăn & Máy cho ăn"
                    )
            )
    );

    @Override
    @Transactional
    public void run(String... args) {
        List<Category> existing = categoryRepository.findAll();

        Map<String, Category> rootsByName = new HashMap<>();
        Set<String> existingSubKeys = new HashSet<>();

        for (Category category : existing) {
            if (category.isRoot()) {
                rootsByName.put(category.getName(), category);
            } else if (category.getParentId() != null) {
                existingSubKeys.add(subKey(category.getParentId(), category.getName()));
            }
        }

        int createdRoots = 0;
        int createdSubs = 0;

        for (CategorySeed seed : SEEDS) {
            Category root = rootsByName.get(seed.name());
            if (root == null) {
                root = categoryRepository.save(new Category(seed.name(), seed.description()));
                rootsByName.put(root.getName(), root);
                createdRoots++;
            } else if ((root.getDescription() == null || root.getDescription().isBlank())
                    && seed.description() != null) {
                root.update(root.getName(), seed.description());
                categoryRepository.save(root);
            }

            UUID rootId = root.getId();
            for (String subName : seed.subCategories()) {
                String key = subKey(rootId, subName);
                if (!existingSubKeys.contains(key)) {
                    categoryRepository.save(new Category(subName, null, root));
                    existingSubKeys.add(key);
                    createdSubs++;
                }
            }
        }

        if (createdRoots > 0 || createdSubs > 0) {
            log.info("Category seeding completed: created {} root(s) and {} sub-category(ies)",
                    createdRoots, createdSubs);
        }
    }

    private static String subKey(UUID parentId, String name) {
        return parentId + "::" + name;
    }
}
