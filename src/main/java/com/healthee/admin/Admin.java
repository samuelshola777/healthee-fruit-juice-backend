package com.healthee.admin;


import com.healthee.utils.BaseEntity;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Document("admin")
public class Admin extends BaseEntity {
  private String userId;
  private boolean active;
  private boolean invitationAccepted;
  private boolean invitationRevoked;
  private boolean deleted;
  private LocalDateTime lastLoginAt;
  private LocalDateTime deletedAt;
  private boolean isSuperAdmin;

}
