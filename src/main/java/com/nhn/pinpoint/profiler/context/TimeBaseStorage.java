package com.nhn.pinpoint.profiler.context;

import com.nhn.pinpoint.profiler.sender.DataSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * @author emeroad
 */
public class TimeBaseStorage implements Storage {
    private static final Logger logger = LoggerFactory.getLogger(TimeBaseStorage.class);
    private static final boolean isDebug = logger.isDebugEnabled();

    private static final int RESERVE_BUFFER_SIZE = 2;
    private static final int DEFAULT_BUFFER_SIZE = 20;

    private boolean discard = true;

    private boolean limit;
    private long limitTime = 1000;
    private final int bufferSize;

    private List<SpanEvent> storage ;
    private final DataSender dataSender;
    private final SpanChunkFactory spanChunkFactory;

    public TimeBaseStorage(DataSender dataSender, SpanChunkFactory spanChunkFactory) {
        this(dataSender, spanChunkFactory, DEFAULT_BUFFER_SIZE);
    }

    public TimeBaseStorage(DataSender dataSender, SpanChunkFactory spanChunkFactory, int bufferSize) {
        if (dataSender == null) {
            throw new NullPointerException("dataSender must not be null");
        }
        if (spanChunkFactory == null) {
            throw new NullPointerException("spanChunkFactory must not be null");
        }
        this.dataSender = dataSender;
        this.spanChunkFactory = spanChunkFactory;
        this.bufferSize = bufferSize;
        this.storage = new ArrayList<SpanEvent>(bufferSize + RESERVE_BUFFER_SIZE);
    }

    public void setDiscard(boolean discard) {
        this.discard = discard;
    }

    public void setLimitTime(long limitTime) {
        this.limitTime = limitTime;
    }

    @Override
    public void store(SpanEvent spanEvent) {
        // flush유무 확인
        if (!limit) {
            // 절대 시간만 체크한다. 1초 이내 라서 절대 데이터를 flush하지 않는다.
            synchronized (this) {
                addSpanEvent(spanEvent);
            }
            limit = checkLimit(spanEvent);
        } else {
            // 1초가 지났다면.
            // 데이터가 flushCount이상일 경우 먼저 flush한다.
            List<SpanEvent> flushData = null;
            synchronized (this) {
                addSpanEvent(spanEvent);
                if (storage.size() >= bufferSize) {
                    // data copy
                    flushData = storage;
                    storage = new ArrayList<SpanEvent>(bufferSize + RESERVE_BUFFER_SIZE);
                }
            }
            if (flushData != null) {
                final SpanChunk spanChunk = spanChunkFactory.create(flushData);
                dataSender.send(spanChunk);
            }
        }
    }

    private void addSpanEvent(SpanEvent spanEvent) {
        final List<SpanEvent> storage = this.storage;
        if (storage == null) {
            if (logger.isErrorEnabled()) {
                logger.error("storage is null. discard spanEvent:{}", spanEvent);
            }
            // 이미 span이 와서 flush된 상황임.
            return;
        }
        storage.add(spanEvent);
    }

    private boolean checkLimit(SpanEvent spanEvent) {
        return checkLimit(spanEvent.getSpan());
    }

    private boolean checkLimit(Span span) {
        long startTime = span.getStartTime();
        long current = System.currentTimeMillis();
        return (current > (startTime + limitTime));
    }

    @Override
    public void store(Span span) {
        // Span이 들어오는것은 마지막 flush타이밍이다.
        // 비동기일 경우는 애매함. 비동기는 개별 flush해야 되나?
        if (discard) {
            limit = checkLimit(span);
            if (!limit) {
                // 제한시간내 빨리 끝난 경우는 subspan을 버린다.
                synchronized (this) {
                    this.storage = null;
                }
                if (isDebug) {
                    logger.debug("discard spanEvent");
                }
                dataSender.send(span);

            } else {
                // 제한 시간이 지났을 경우 모두 flush
                flushAll(span);
            }
        } else {
            flushAll(span);
        }
    }

    private void flushAll(Span span) {
        List<SpanEvent> spanEventList;
        synchronized (this) {
            spanEventList = storage;
            this.storage = null;
        }
        if (spanEventList != null && !spanEventList.isEmpty()) {
            span.setSpanEventList((List) spanEventList);
        }
        dataSender.send(span);
    }

}
