package com.scms.service;

import com.scms.dao.MaterialDao;
import com.scms.model.Material;

import java.sql.SQLException;
import java.util.List;

public class MaterialService {

    private final MaterialDao materialDao = new MaterialDao();

    public Material createMaterial(Material m) {
        if (m == null) throw new ServiceException("material.required");
        if (m.getName() == null || m.getName().isBlank()) throw new ServiceException("material.name.required");
        try {
            return materialDao.create(m);
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

