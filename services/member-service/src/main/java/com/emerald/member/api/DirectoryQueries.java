package com.emerald.member.api;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import com.emerald.member.entity.MemberEntity;
import com.emerald.member.security.MemberAccess;
@Service
public class DirectoryQueries {
 private final EntityManager em; private final MemberAccess access;
 public DirectoryQueries(EntityManager em,MemberAccess access) { this.em=em;this.access=access; }
 public static String search(String value) { String s=value==null?"":value.trim().toLowerCase(Locale.ROOT);if(s.length()>120)throw new ApiFailure(400,"INVALID_SEARCH","Search must be at most 120 characters");return s; }
 public static String status(String value) { String s=value==null?"":value.trim().toUpperCase(Locale.ROOT);if(!s.isEmpty()&&!Set.of("ACTIVE","INACTIVE").contains(s))throw new ApiFailure(400,"INVALID_STATUS","Status must be ACTIVE or INACTIVE");return s; }
 private List<Predicate> predicates(CriteriaBuilder cb,Root<MemberEntity> root,String search,String status,Integer floor) {
  if(!access.read())throw new org.springframework.security.access.AccessDeniedException("No directory access");
  var result=new ArrayList<Predicate>();
  if(access.resident()) result.add(cb.equal(root.get("id"),access.ownMember()));
  if(!status.isEmpty())result.add(cb.equal(root.get("status").as(String.class),status));
  if(!search.isEmpty()) {
   String pattern="%"+search.replace("!","!!").replace("%","!%").replace("_","!_")+"%";
   result.add(cb.or(cb.like(cb.lower(root.get("fullName")),pattern,'!'),cb.like(cb.lower(root.get("mobileNumber")),pattern,'!'),cb.like(cb.lower(root.get("email")),pattern,'!')));
  }
  return result;
 }
 @Transactional(readOnly=true)
 public Page<MemberEntity> page(String search,String status,Integer floor,Pageable page) {
  search=search(search);status=status(status);
  var cb=em.getCriteriaBuilder();var query=cb.createQuery(MemberEntity.class);var root=query.from(MemberEntity.class);
  
  query.where(predicates(cb,root,search,status,floor).toArray(Predicate[]::new));
  // Keep this collection mutable: the deterministic ID tie-breaker is appended below.
  List<Order> orders=page.getSort().stream()
   .map(sort->sort.isAscending()?cb.asc(root.get(sort.getProperty())):cb.desc(root.get(sort.getProperty())))
   .collect(Collectors.toCollection(ArrayList::new));
  orders.add(cb.asc(root.get("id")));query.orderBy(orders);
  if(page.getOffset()>Integer.MAX_VALUE)throw new ApiFailure(400,"INVALID_PAGE","Use cursor pagination for deep browsing");
  var rows=em.createQuery(query).setFirstResult((int)page.getOffset()).setMaxResults(page.getPageSize()).getResultList();
  var count=cb.createQuery(Long.class);var countRoot=count.from(MemberEntity.class);count.select(cb.count(countRoot));count.where(predicates(cb,countRoot,search,status,floor).toArray(Predicate[]::new));
  return new PageImpl<>(rows,page,em.createQuery(count).getSingleResult());
 }
 @Transactional(readOnly=true)
 public CursorPage<MemberEntity> cursor(String search,String status,Integer floor,int limit,String after) {
  if(limit<1||limit>100)throw new ApiFailure(400,"INVALID_PAGE","limit must be between 1 and 100");
  search=search(search);status=status(status);
  String scope=CursorCodec.scope(search+"|"+status+"|"+floor+"|"+access.identity().adminId()+"|"+access.identity().role()+"|"+access.ownMember()+"|"+access.ownFlats().stream().sorted(Comparator.naturalOrder()).toList());
  var position=CursorCodec.decode(after,scope);
  var cb=em.getCriteriaBuilder();var query=cb.createQuery(MemberEntity.class);var root=query.from(MemberEntity.class);
  
  var where=predicates(cb,root,search,status,floor);
  if(position!=null) where.add(cb.or(cb.greaterThan(root.<Instant>get("createdAt"),position.createdAt()),cb.and(cb.equal(root.get("createdAt"),position.createdAt()),cb.greaterThan(root.<UUID>get("id"),position.id()))));
  query.where(where.toArray(Predicate[]::new));query.orderBy(cb.asc(root.get("createdAt")),cb.asc(root.get("id")));
  var rows=em.createQuery(query).setMaxResults(limit+1).getResultList();boolean more=rows.size()>limit;
  var items=List.copyOf(rows.subList(0,Math.min(limit,rows.size())));var last=items.isEmpty()?null:items.get(items.size()-1);
  return new CursorPage<>(items,more?CursorCodec.encode(last.getCreatedAt(),last.getId(),scope):null,more);
 }
}
