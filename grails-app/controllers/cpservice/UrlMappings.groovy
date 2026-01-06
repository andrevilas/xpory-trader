package cpservice

class UrlMappings {

    static mappings = {
        "/wls"(controller: 'whiteLabel') {
            action = [GET: 'index', POST: 'save']
        }
        "/wls/$id"(controller: 'whiteLabel') {
            action = [GET: 'show', PUT: 'update']
        }
        "/wls/$id/policies"(controller: 'whiteLabel') {
            action = [GET: 'policies', PUT: 'updatePolicies']
        }
        "/wls/$id/policies/revisions"(controller: 'whiteLabel', action: 'policyRevisions', method: 'GET')
        "/wls/$id/trader"(controller: 'traderAccount') {
            action = [GET: 'show', POST: 'upsert', PUT: 'upsert']
        }
        "/wls/$id/trader/sync"(controller: 'traderAccount', action: 'sync', method: 'POST')
        "/wls/$id/peer-token"(controller: 'whiteLabel', action: 'peerToken', method: 'POST')
        "/wls/$id/token"(controller: 'whiteLabel', action: 'token', method: 'POST')
        "/wls/$id/offer-categories"(controller: 'exportMetadata', action: 'categories', method: 'GET')
        "/wls/$id/offer-categories/sync"(controller: 'exportMetadata', action: 'syncCategories', method: 'POST')
        "/wls/$id/entities"(controller: 'exportMetadata', action: 'entities', method: 'GET')
        "/wls/$id/entities/sync"(controller: 'exportMetadata', action: 'syncEntities', method: 'POST')
        "/relationships"(controller: 'relationship', action: 'index', method: 'GET')
        "/wls/$id/relationships"(controller: 'relationship', action: 'index', method: 'GET')
        "/relationships/$src/$dst"(controller: 'relationship') {
            action = [GET: 'show', PUT: 'update']
        }
        "/policies/pull"(controller: 'policyAgent', action: 'pull', method: 'POST')
        "/imbalance/signals"(controller: 'imbalance', action: 'submit', method: 'POST')
        "/imbalance/signals/$id/ack"(controller: 'imbalance', action: 'ack', method: 'POST')
        "/telemetry/events"(controller: 'telemetry') {
            action = [GET: 'list', POST: 'events']
        }
        "/reports/trade-balance"(controller: 'reports', action: 'tradeBalance', method: 'GET')
        "/trades"(controller: 'trade', action: 'index', method: 'GET')
        "/trades/pending"(controller: 'tradeApproval', action: 'pending', method: 'GET')
        "/trades/$tradeId/approve"(controller: 'tradeApproval', action: 'approve', method: 'POST')
        "/trades/$tradeId/reject"(controller: 'tradeApproval', action: 'reject', method: 'POST')
        "/trades/$tradeId/details"(controller: 'tradeApproval', action: 'details', method: 'GET')
        group "/api/v2", {
            "/trader/purchases/pending"(controller: 'tradeApproval', action: 'pending', method: 'GET')
            "/trader/purchases/$tradeId/approve"(controller: 'tradeApproval', action: 'approve', method: 'POST')
            "/trader/purchases/$tradeId/reject"(controller: 'tradeApproval', action: 'reject', method: 'POST')
            "/control-plane/trader/purchases/$tradeId/details"(controller: 'tradeApproval', action: 'details', method: 'GET')
            "/trader/purchases/$tradeId/details"(controller: 'tradeApproval', action: 'details', method: 'GET')
        }
        "/auth/login"(controller: 'auth', action: 'login', method: 'POST')
        "/users/me"(controller: 'adminProfile', action: 'show', method: 'GET')
        "/users/me"(controller: 'adminProfile', action: 'update', method: 'PUT')
        "/users/me/password"(controller: 'adminProfile', action: 'updatePassword', method: 'PUT')
        "/users"(controller: 'adminUser') {
            action = [GET: 'index', POST: 'save']
        }
        "/users/$id"(controller: 'adminUser', action: 'update', method: 'PUT') {
            constraints {
                id matches: /[0-9a-fA-F-]{36}/
            }
        }
        "/users/$id/reset-password"(controller: 'adminUser', action: 'resetPassword', method: 'POST') {
            constraints {
                id matches: /[0-9a-fA-F-]{36}/
            }
        }
        "/notifications"(controller: 'notification', action: 'index', method: 'GET')
        "/notifications/unread-count"(controller: 'notification', action: 'unreadCount', method: 'GET')
        "/notifications/$id/read"(controller: 'notification', action: 'read', method: 'POST') {
            constraints {
                id matches: /[0-9a-fA-F-]{36}/
            }
        }
        "/.well-known/jwks.json"(controller: 'jwks', action: 'index', method: 'GET')
        "/wls/$id/keys/rotate"(controller: 'key', action: 'rotate', method: 'POST')

        "/$controller/$action?/$id?(.$format)?"{
            constraints {
                // apply constraints here
            }
        }

        "/"(view:"/index")
        "500"(view:'/error')
        "404"(view:'/notFound')
    }
}
