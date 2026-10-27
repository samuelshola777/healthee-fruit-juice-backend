package com.healthee.admin;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AdminRepository extends MongoRepository<Admin, String> {
    Optional<Admin> findByUserId(String userId);

    int countByDeleted(boolean b);

    boolean existsByUserId(String id);

    Optional <Admin> findByUserIdAndDeleted(String id, boolean b);

    Collection<Admin> findAllByDeleted(boolean b);

    long countByDeletedAndActive(boolean b, boolean b1);

    Optional<Admin> findByIdAndDeleted(String adminId, boolean b);

    boolean existsByIsSuperAdmin(boolean b);

    boolean existsByIsSuperAdminAndDeleted(boolean isSuperAdmin, boolean deleted);
}