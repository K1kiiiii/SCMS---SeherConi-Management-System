package com.scms.service;

import com.scms.dao.MaterialDao;
import com.scms.dao.InventoryMovementDao;
import com.scms.model.InventoryMovement;
import com.scms.model.Material;
import com.scms.util.RoleManager;

import java.sql.SQLException;
import java.util.List;

public class MaterialService {

    private final MaterialDao materialDao = new MaterialDao();
    private final InventoryMovementDao inventoryMovementDao = new InventoryMovementDao();

    public Material createMaterial(Material m) {
        if (m == null) throw new ServiceException("material.required");
        if (m.getName() == null || m.getName().isBlank()) throw new ServiceException("material.name.required");
        try {
            Material created = materialDao.create(m);

            // If purchase price was provided, create an IN inventory movement to record import cost
            if (created.getLastPurchasePrice() != null && created.getQuantity() > 0) {
                try {
                    InventoryMovement mv = new InventoryMovement();
                    mv.setMaterialId(created.getId());
                    mv.setType("IN");
                    mv.setQuantity(created.getQuantity());
                    mv.setUnitPrice(created.getLastPurchasePrice());
                    mv.setTotalPrice(created.getLastPurchasePrice() * created.getQuantity());
                    try { mv.setUserId(RoleManager.getLoggedInUser().getId()); } catch (Exception ignored) {}
                    inventoryMovementDao.insert(mv);
                } catch (Exception ex) {
                    // don't fail material creation if movement logging fails; log to stderr
                    System.err.println("Failed logging inventory movement for created material: " + ex.getMessage());
                }
            }

            return created;
        } catch (SQLException ex) {
            throw new ServiceException("Failed creating material", ex);
        }
    }

    public Material getMaterial(int id) {
        try {
            return materialDao.findById(id).orElseThrow(() -> new ServiceException("material.not_found"));
        } catch (SQLException ex) {
            throw new ServiceException("Failed fetching material", ex);
        }
    }

    public List<Material> listMaterials() {
        try {
            return materialDao.findAll();
        } catch (SQLException ex) {
            throw new ServiceException("Failed fetching materials", ex);
        }
    }

    public Material updateMaterial(Material m) {
        if (m == null || m.getId() <= 0) throw new ServiceException("material.invalid");
        try {
            return materialDao.update(m).orElseThrow(() -> new ServiceException("material.not_found"));
        } catch (SQLException ex) {
            throw new ServiceException("Failed updating material", ex);
        }
    }

    public void deleteMaterial(int id) {
        try {
            if (!materialDao.delete(id)) throw new ServiceException("material.not_found");
        } catch (SQLException ex) {
            throw new ServiceException("Failed deleting material", ex);
        }
    }
}
