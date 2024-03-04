package it.smartcommunitylabdhub.core.repositories;

import it.smartcommunitylabdhub.core.models.entities.runnable.RunnableEntity;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RunnableRepository {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public void save(RunnableEntity entity) {
        String sql = "INSERT INTO runnable (id, data) VALUES (?, ?)";
        jdbcTemplate.update(sql, entity.getId(), entity.getData());
    }

    public void update(RunnableEntity entity) {
        String sql = "UPDATE runnable SET data = ? WHERE id = ?";
        jdbcTemplate.update(sql, entity.getData(), entity.getId());
    }

    public RunnableEntity findById(String id) {

        try {
            String sql = "SELECT * FROM runnable WHERE id = ?";
            return jdbcTemplate.queryForObject(sql, BeanPropertyRowMapper.newInstance(RunnableEntity.class), id);
        } catch (Exception e) {
            return null;
        }

    }

    public List<RunnableEntity> findAll() {
        try {
            String sql = "SELECT * FROM runnable";
            return jdbcTemplate.query(sql, BeanPropertyRowMapper.newInstance(RunnableEntity.class));
        } catch (Exception e) {
            return List.of();
        }
    }

    public void delete(String id) {
        String sql = "DELETE FROM runnable WHERE id = ?";
        jdbcTemplate.update(sql, id);
    }
}
