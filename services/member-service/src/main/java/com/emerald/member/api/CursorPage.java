package com.emerald.member.api;
import java.util.List;
public record CursorPage<T>(List<T> items,String nextCursor,boolean hasNext) {
 public CursorPage { items=List.copyOf(items); }
}
