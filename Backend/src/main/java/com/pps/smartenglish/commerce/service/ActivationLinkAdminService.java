package com.pps.smartenglish.commerce.service;

import com.pps.smartenglish.auth.service.TokenUtil;
import com.pps.smartenglish.commerce.domain.ActivationLink;
import com.pps.smartenglish.commerce.dto.ActivationLinkDtos.Created;
import com.pps.smartenglish.commerce.dto.ActivationLinkDtos.Revoked;
import com.pps.smartenglish.commerce.dto.ActivationLinkView;
import com.pps.smartenglish.commerce.dto.CreateActivationLinkRequest;
import com.pps.smartenglish.commerce.repository.ActivationLinkRepository;
import com.pps.smartenglish.commerce.repository.ProductRepository;
import com.pps.smartenglish.shared.error.ApiException;
import com.pps.smartenglish.shared.web.CursorPage;
import com.pps.smartenglish.shared.web.Cursors;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** FR-COM-04, phía Admin: tạo, xem và thu hồi link kích hoạt. */
@Service
public class ActivationLinkAdminService {

    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 100;

    private final ActivationLinkRepository links;
    private final ProductRepository products;
    private final Clock clock;
    private final String baseUrl;

    ActivationLinkAdminService(ActivationLinkRepository links, ProductRepository products, Clock clock,
                               @Value("${app.base-url}") String baseUrl) {
        this.links = links;
        this.products = products;
        this.clock = clock;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /** Mã thô chỉ nằm trong URL trả về đúng một lần; DB chỉ lưu hash và mã không được ghi log. */
    @Transactional
    public Created create(long adminId, CreateActivationLinkRequest req) {
        var product = products.findById(req.productId()).filter(p -> p.isActive())
                .orElseThrow(() -> ApiException.validation("productId", "Sản phẩm không tồn tại hoặc đã ngừng bán"));
        String token = TokenUtil.newToken();
        var link = links.save(new ActivationLink(TokenUtil.hash(token), product.getId(), req.maxUses(), req.expiresAt(),
                req.note(), adminId));
        return new Created(link.getId(), baseUrl + "/activate/" + token);
    }

    @Transactional(readOnly = true)
    public CursorPage<ActivationLinkView> list(int limit, String cursor) {
        List<ActivationLinkView> rows = links.findPage(Cursors.decode(cursor), PageRequest.of(0, limit + 1));
        boolean more = rows.size() > limit;
        List<ActivationLinkView> page = more ? rows.subList(0, limit) : rows;
        return new CursorPage<>(page, more ? Cursors.encode(page.get(limit - 1).id()) : null);
    }

    /** Gọi lại vẫn 200 và giữ thời điểm thu hồi đầu tiên. Quyền đã cấp không bị gỡ. */
    @Transactional
    public Revoked revoke(Long id) {
        if (!links.existsById(id)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "activation_link_not_found", "Không tìm thấy link kích hoạt");
        }
        links.revoke(id, Instant.now(clock));
        return new Revoked(id, links.findById(id).orElseThrow().getRevokedAt());
    }
}
