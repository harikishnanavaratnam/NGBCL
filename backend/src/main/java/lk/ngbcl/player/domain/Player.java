package lk.ngbcl.player.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lk.ngbcl.shared.domain.AuditedEntity;

/** Reusable player profile across editions (SRS §6.2). Contact fields are private. */
@Entity
@Table(name = "players")
public class Player extends AuditedEntity {

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "gender", length = 20)
    private String gender;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "phone", nullable = false, length = 30)
    private String phone;

    @Column(name = "address", length = 300)
    private String address;

    @Column(name = "home_area", length = 80)
    private String homeArea;

    @Column(name = "profile_photo_asset_id")
    private UUID profilePhotoAssetId;

    @Column(name = "slug", nullable = false, length = 140)
    private String slug;

    protected Player() {
    }

    public Player(String fullName, LocalDate dateOfBirth, String gender, String email, String phone, String address,
            String homeArea, UUID photoAssetId, String slug) {
        this.slug = slug;
        update(fullName, dateOfBirth, gender, email, phone, address, homeArea, photoAssetId);
    }

    /** Latest approved registration wins (the registration keeps what was submitted). */
    public void update(String fullName, LocalDate dateOfBirth, String gender, String email, String phone,
            String address, String homeArea, UUID photoAssetId) {
        this.fullName = fullName;
        this.dateOfBirth = dateOfBirth;
        this.gender = gender;
        this.email = email;
        this.phone = phone;
        this.address = address;
        this.homeArea = homeArea;
        if (photoAssetId != null) {
            this.profilePhotoAssetId = photoAssetId;
        }
    }

    public String getFullName() {
        return fullName;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public String getGender() {
        return gender;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getHomeArea() {
        return homeArea;
    }

    public UUID getProfilePhotoAssetId() {
        return profilePhotoAssetId;
    }

    public String getSlug() {
        return slug;
    }
}
