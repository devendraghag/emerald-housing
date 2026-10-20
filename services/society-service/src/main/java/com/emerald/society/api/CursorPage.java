package com.emerald.society.api;
import java.util.List;
public record CursorPage<T>(List<T> items,String nextCursor,boolean hasNext) { }
