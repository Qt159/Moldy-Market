package com.moldy.moldymarket.config;

import com.moldy.moldymarket.category.entity.Category;
import com.moldy.moldymarket.category.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Order(3)
@RequiredArgsConstructor
public class CategoryInitializer implements CommandLineRunner {

    private final CategoryRepository categoryRepository;

    private static final Map<String, String[]> SEED = Map.of(
            "Đồ điện tử", new String[]{"Điện thoại", "Laptop", "Máy tính bảng", "Tai nghe", "Phụ kiện điện tử"},
            "Đồ nội thất", new String[]{"Bàn", "Ghế", "Tủ", "Giường", "Đèn"},
            "Thời trang", new String[]{"Áo", "Quần", "Giày", "Túi xách", "Phụ kiện"},
            "Sách", new String[]{"Văn học", "Kinh tế", "Khoa học", "Công nghệ", "Giáo dục"}
    );

    @Override
    public void run(String... args) {
        if (categoryRepository.count() > 0) {
            return;
        }

        SEED.forEach((rootName, subNames) -> {
            Category root = categoryRepository.save(Category.createRoot(rootName, null));
            for (String subName : subNames) {
                categoryRepository.save(Category.createSubCategory(subName, null, root));
            }
        });
    }
}
