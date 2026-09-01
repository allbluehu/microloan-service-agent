package com.example.microloan.knowledge;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeQueryNormalizer {

    public KnowledgeQuery normalize(String message, String selectedProductCode, String selectedRegion) {
        String productCode = selectedProductCode;
        List<String> terms = new ArrayList<>();
        boolean recognizedTopic = false;
        if (message.contains("经营贷") || message.contains("个体工商户")) {
            productCode = productCode == null ? "MICRO_BUSINESS" : productCode;
            terms.add("经营贷");
        } else if (message.contains("消费贷")) {
            productCode = productCode == null ? "PERSONAL_CONSUMER" : productCode;
            terms.add("消费贷");
        }
        if (message.contains("材料") || message.contains("准备什么")) {
            terms.add("材料");
            recognizedTopic = true;
        }
        if (message.contains("多少期") || message.contains("期限") || message.contains("多久")) {
            terms.add("期限");
            recognizedTopic = true;
        }
        if (message.contains("利率")) {
            terms.add("利率");
            recognizedTopic = true;
        }
        if (message.contains("费用")) {
            terms.add("费用");
            recognizedTopic = true;
        }
        if (message.contains("流程")) {
            terms.add("流程");
            recognizedTopic = true;
        }
        if (message.contains("条件")) {
            terms.add("条件");
            recognizedTopic = true;
        }
        // A product match alone must not broaden an unknown topic into every chunk for that product.
        String searchText = recognizedTopic ? String.join(" ", terms) : message.strip();
        String region = selectedRegion == null || selectedRegion.isBlank() ? "ALL" : selectedRegion;
        return new KnowledgeQuery(searchText, productCode, region);
    }
}
