package com.emerald.society.dto;
import java.util.UUID;
public record FlatResponse(UUID flatId,String flatNumber,UUID floorId,int floorNumber,String status,long version) {
 public FlatResponse(UUID id,String number,UUID floor,int floorNumber,String status) { this(id,number,floor,floorNumber,status,0); }
}
