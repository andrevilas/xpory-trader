package cpservice

import grails.gorm.transactions.Transactional
import groovy.sql.Sql

import javax.sql.DataSource

@Transactional
class WhiteLabelIdentityService {

    DataSource dataSource

    WhiteLabel renameWhiteLabelId(String currentId, String newId) {
        if (!currentId || !newId) {
            throw new IllegalArgumentException('id is required')
        }
        if (currentId == newId) {
            return WhiteLabel.get(currentId)
        }
        if (WhiteLabel.get(newId)) {
            throw new IllegalArgumentException('id already in use')
        }

        Sql sql = new Sql(dataSource)
        sql.withTransaction {
            sql.executeUpdate('UPDATE cp_relationships SET source_id = ? WHERE source_id = ?', [newId, currentId])
            sql.executeUpdate('UPDATE cp_relationships SET target_id = ? WHERE target_id = ?', [newId, currentId])
            sql.executeUpdate('UPDATE cp_imbalance_signals SET source_id = ? WHERE source_id = ?', [newId, currentId])
            sql.executeUpdate('UPDATE cp_imbalance_signals SET target_id = ? WHERE target_id = ?', [newId, currentId])
            sql.executeUpdate('UPDATE cp_white_labels SET id = ? WHERE id = ?', [newId, currentId])
        }

        return WhiteLabel.get(newId)
    }
}
