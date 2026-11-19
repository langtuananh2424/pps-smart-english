package com.pps.smartenglish.commerce.service;

import com.pps.smartenglish.catalog.repository.CourseLevelRepository;
import com.pps.smartenglish.commerce.domain.Product;
import com.pps.smartenglish.commerce.dto.ProductAdminDtos.Create;
import com.pps.smartenglish.commerce.dto.ProductAdminDtos.Patch;
import com.pps.smartenglish.commerce.dto.ProductAdminDtos.View;
import com.pps.smartenglish.commerce.repository.ActivationLinkRepository;
import com.pps.smartenglish.commerce.repository.OrderItemRepository;
import com.pps.smartenglish.commerce.repository.ProductRepository;
import com.pps.smartenglish.shared.audit.AuditRecorder;
import com.pps.smartenglish.shared.error.ApiException;
import com.pps.smartenglish.shared.persistence.CmsWrites;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-COM-07, D-127: admin tạo, sửa, bật/tắt gói bán; số mức thời hạn của mỗi khóa do admin quyết định.
 * Đổi giá không ảnh hưởng đơn đã tạo (order_items giữ giá lúc mua). Thời hạn và lượt AI quyết định quyền lợi lúc kích hoạt,
 * nên chỉ sửa được khi chưa có đơn hoặc link nào dùng gói; muốn đổi thì tạo gói mới và tắt gói cũ.
 */
@Service
public class ProductAdminService {

    private final ProductRepository products;
    private final CourseLevelRepository levels;
    private final OrderItemRepository orderItems;
    private final ActivationLinkRepository links;
    private final CmsWrites writes;
    private final AuditRecorder audit;

    ProductAdminService(ProductRepository products, CourseLevelRepository levels, OrderItemRepository orderItems,
                        ActivationLinkRepository links, CmsWrites writes, AuditRecorder audit) {
        this.products = products;
        this.levels = levels;
        this.orderItems = orderItems;
        this.links = links;
        this.writes = writes;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<View> list() {
        return products.findAllWithLevels().stream().map(ProductAdminService::view).toList();
    }

    @Transactional
    public View create(Create req) {
        List<Long> levelIds = req.levelIds() == null ? List.of() : req.levelIds().stream().distinct().toList();
        if (Product.LEVEL.equals(req.type())) {
            requireLevelProduct(req, levelIds);
        } else {
            requireAddon(req, levelIds);
        }
        var product = new Product(req.type(), req.name().strip(), req.priceVnd(), req.accessDays(), req.aiQuota(), levelIds);
        requireNoDuplicate(product, null);
        var after = view(writes.save(products, product, "product_conflict", "Không lưu được gói bán"));
        audit.record("CREATE", "PRODUCT", after.id(), null, after);
        return after;
    }

    @Transactional
    public View patch(long id, Patch req) {
        Product product = products.findWithLevels(List.of(id)).stream().findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "product_not_found", "Không tìm thấy gói bán"));
        var before = view(product);
        boolean changesEntitlement = (req.accessDays() != null && !req.accessDays().equals(product.getAccessDays()))
                || (req.aiQuota() != null && !req.aiQuota().equals(product.getAiQuota()));
        if (changesEntitlement && inUse(id)) {
            throw new ApiException(HttpStatus.CONFLICT, "product_in_use",
                    "Đã có đơn hoặc link kích hoạt dùng gói này, hãy tạo gói mới với thời hạn hoặc lượt AI mong muốn và tắt gói cũ");
        }
        if (req.accessDays() != null) {
            if (Product.AI_ADDON.equals(product.getType())) {
                throw ApiException.validation("accessDays", "Gói lượt AI không có thời hạn");
            }
            product.setAccessDays(req.accessDays());
        }
        if (req.aiQuota() != null) {
            if (Product.AI_ADDON.equals(product.getType()) && req.aiQuota() < 1) {
                throw ApiException.validation("aiQuota", "Gói lượt AI cần ít nhất 1 lượt");
            }
            product.setAiQuota(req.aiQuota());
        }
        if (req.name() != null) {
            if (req.name().isBlank()) {
                throw ApiException.validation("name", "Không được để trống");
            }
            product.setName(req.name().strip());
        }
        if (req.priceVnd() != null) {
            product.setPriceVnd(req.priceVnd());
        }
        if (req.status() != null) {
            product.setStatus(req.status());
        }
        requireNoDuplicate(product, id);
        var after = view(writes.save(products, product, "product_conflict", "Không lưu được gói bán"));
        audit.record("UPDATE", "PRODUCT", id, before, after);
        return after;
    }

    private boolean inUse(long id) {
        return orderItems.existsByProductId(id) || links.existsByProductId(id);
    }

    /** Gói khóa học: đúng một level đã tồn tại, có thời hạn (không combo, D-03). */
    private void requireLevelProduct(Create req, List<Long> levelIds) {
        if (levelIds.size() != 1) {
            throw ApiException.validation("levelIds", "Gói khóa học cần đúng một level");
        }
        if (req.accessDays() == null) {
            throw ApiException.validation("accessDays", "Bắt buộc với gói khóa học");
        }
        if (!levels.existsById(levelIds.get(0))) {
            throw ApiException.validation("levelIds", "Không tìm thấy level");
        }
    }

    private void requireAddon(Create req, List<Long> levelIds) {
        if (!levelIds.isEmpty()) {
            throw ApiException.validation("levelIds", "Gói lượt AI không gắn level");
        }
        if (req.accessDays() != null) {
            throw ApiException.validation("accessDays", "Gói lượt AI không có thời hạn");
        }
        if (req.aiQuota() == null || req.aiQuota() < 1) {
            throw ApiException.validation("aiQuota", "Gói lượt AI cần ít nhất 1 lượt");
        }
    }

    /** Một level không có hai gói đang bán cùng thời hạn. Danh mục nhỏ nên so trong bộ nhớ. */
    private void requireNoDuplicate(Product p, Long selfId) {
        if (!p.isActive() || !Product.LEVEL.equals(p.getType())) {
            return;
        }
        boolean dup = products.findActiveLevelProducts().stream()
                .anyMatch(o -> !Objects.equals(o.getId(), selfId) && o.getLevelIds().equals(p.getLevelIds())
                        && Objects.equals(o.getAccessDays(), p.getAccessDays()));
        if (dup) {
            throw new ApiException(HttpStatus.CONFLICT, "product_duplicate", "Level này đã có gói đang bán với cùng thời hạn");
        }
    }

    private static View view(Product p) {
        return new View(p.getId(), p.getType(), p.getName(), p.getPriceVnd(), p.getAccessDays(), p.getAiQuota(), p.getStatus(),
                List.copyOf(p.getLevelIds()));
    }
}
