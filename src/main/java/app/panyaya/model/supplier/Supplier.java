package app.panyaya.model.supplier;

import app.panyaya.exception.ValidationException;
import app.panyaya.model.BaseEntity;
import app.panyaya.util.TextUtil;

/** A company or market stall that delivers ingredients or resale products. */
public class Supplier extends BaseEntity {

    private String name;
    private String contactPerson;
    private String phone;
    private String email;
    private String address;
    private String notes;
    private boolean active = true;

    private Supplier() {
    }

    public Supplier(long id, String name, String contactPerson, String phone, String email, String address,
                    String notes) throws ValidationException {
        super(id);
        update(name, contactPerson, phone, email, address, notes);
    }

    public void update(String newName, String newContact, String newPhone, String newEmail, String newAddress,
                       String newNotes) throws ValidationException {
        String cleanName = TextUtil.normalize(newName);
        if (cleanName.isEmpty() || cleanName.length() > 80) {
            throw new ValidationException("Supplier name must be 1-80 characters.");
        }
        String cleanEmail = TextUtil.normalize(newEmail);
        if (!cleanEmail.isEmpty() && !cleanEmail.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            throw new ValidationException("E-mail address \"" + cleanEmail + "\" looks invalid.");
        }
        String cleanPhone = TextUtil.normalize(newPhone);
        if (!cleanPhone.isEmpty() && !cleanPhone.matches("[0-9+()\\- ]{5,20}")) {
            throw new ValidationException("Phone number may only contain digits, spaces, +, - and brackets.");
        }
        this.name = cleanName;
        this.contactPerson = TextUtil.normalize(newContact);
        this.phone = cleanPhone;
        this.email = cleanEmail;
        this.address = TextUtil.normalize(newAddress);
        this.notes = TextUtil.normalize(newNotes);
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getName() {
        return name;
    }

    public String getContactPerson() {
        return contactPerson;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public String getAddress() {
        return address;
    }

    public String getNotes() {
        return notes;
    }

    public boolean isActive() {
        return active;
    }

    @Override
    public String toString() {
        return name;
    }
}
