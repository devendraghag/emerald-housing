package com.emerald.member.dto;
import java.util.UUID;
public record SocietyFlatResponse(UUID flatId,String flatNumber,UUID floorId,int floorNumber,String status,Long version) {
 public SocietyFlatResponse { version=version==null?0L:version; }
 public SocietyFlatResponse(UUID id,String number,UUID floor,int floorNumber,String status){this(id,number,floor,floorNumber,status,0L);}
}
