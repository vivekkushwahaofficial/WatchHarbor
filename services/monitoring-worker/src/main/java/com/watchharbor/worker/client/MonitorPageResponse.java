
package com.watchharbor.worker.client;

import java.util.List;

public record MonitorPageResponse(
        List<MonitorResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
}
