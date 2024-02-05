package it.smartcommunitylabdhub.core.repositories;

import it.smartcommunitylabdhub.core.models.entities.runnable.RunnableEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class RunnableRepository {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public void save(RunnableEntity entity) {
        String sql = "INSERT INTO runnable (id, data) VALUES (?, ?)";
        jdbcTemplate.update(sql, entity.getId(), entity.getData());
    }

    public RunnableEntity findById(String id) {
        String sql = "SELECT * FROM runnable WHERE id = ?";
        return jdbcTemplate.queryForObject(sql, new Object[]{id}, BeanPropertyRowMapper.newInstance(RunnableEntity.class));
    }

    public List<RunnableEntity> findAll() {
        String sql = "SELECT * FROM runnable";
        return jdbcTemplate.query(sql, BeanPropertyRowMapper.newInstance(RunnableEntity.class));
    }

    public void delete(String id) {
        String sql = "DELETE FROM runnable WHERE id = ?";
        jdbcTemplate.update(sql, id);
    }
}