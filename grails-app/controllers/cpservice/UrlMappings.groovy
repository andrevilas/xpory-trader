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
        "/wls/$id/trader"(controller: 'traderAccount') {
            action = [GET: 'show', POST: 'upsert', PUT: 'upsert']
        }
        "/wls/$id/token"(controller: 'whiteLabel', action: 'token', method: 'POST')
        "/relationships/$src/$dst"(controller: 'relationship') {
            action = [GET: 'show', PUT: 'update']
        }
        "/policies/pull"(controller: 'policyAgent', action: 'pull', method: 'POST')
        "/imbalance/signals"(controller: 'imbalance', action: 'submit', method: 'POST')
        "/imbalance/signals/$id/ack"(controller: 'imbalance', action: 'ack', method: 'POST')
        "/telemetry/events"(controller: 'telemetry', action: 'events', method: 'POST')
        "/reports/trade-balance"(controller: 'reports', action: 'tradeBalance', method: 'GET')
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
