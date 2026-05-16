package com.mars.linker.broker.ui.rejection;

import com.mars.linker.broker.ui.monitoring.model.PagedResult;
import com.mars.linker.broker.ui.rejection.model.RejectionMessage;
import com.mars.linker.broker.ui.rejection.model.RejectionReason;
import com.mars.linker.broker.ui.rejection.model.RejectionSummary;
import com.mars.linker.broker.ui.rejection.model.RejectionType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "mars.linker.ui", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RejectionMessageService {

    private final RejectionMessageStore store;

    public RejectionMessageService(RejectionMessageStore store) {
        this.store = store;
    }

    public PagedResult<RejectionMessage> listMessages(int page, int size, RejectionType type, RejectionReason reason) {
        return store.listPaged(page, size, type, reason);
    }

    public boolean removeMessage(long id) {
        return store.removeById(id);
    }

    public long removeMessages(RejectionType type) {
        if (type == null) {
            return store.removeAll();
        }
        return store.removeByType(type);
    }

    public RejectionSummary getSummary() {
        return store.getSummary();
    }
}
