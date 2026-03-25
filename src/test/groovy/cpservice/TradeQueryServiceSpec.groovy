package cpservice

import grails.testing.gorm.DataTest
import grails.testing.services.ServiceUnitTest
import spock.lang.Specification

class TradeQueryServiceSpec extends Specification implements ServiceUnitTest<TradeQueryService>, DataTest {

    def setupSpec() {
        mockDomains TradeProjection, WhiteLabel
    }

    void setup() {
        service.tradeApprovalService = Stub(TradeApprovalService)
    }

    void "list enriches trades with operational details and filters by client and value"() {
        given:
        WhiteLabel exporter = new WhiteLabel(id: 'wl-exp', name: 'Alpha', gatewayUrl: 'https://alpha.test', contactEmail: 'alpha@test.com')
                .save(validate: false, flush: true)
        WhiteLabel importer = new WhiteLabel(id: 'wl-imp', name: 'Beta', gatewayUrl: 'https://beta.test', contactEmail: 'beta@test.com')
                .save(validate: false, flush: true)

        new TradeProjection(
                tradeExternalId: 'ext-1',
                tradeId: 'trade-1',
                originWhiteLabelId: exporter.id,
                targetWhiteLabelId: importer.id,
                status: 'CONFIRMED',
                approvalMode: 'AUTO',
                unitPrice: 10G,
                confirmedQuantity: 2,
                lastEventTimestamp: new Date()
        ).save(validate: false, flush: true)

        service.tradeApprovalService = Stub(TradeApprovalService) {
            getDetails('ext-1') >> [
                    originOfferId: 'offer-1',
                    offerName    : 'Oferta XPTO',
                    exporter     : [clientName: 'Fornecedor XPTO', clientPhone: '+55 11 99999-1111', price: 10G],
                    importer     : [clientName: 'Comprador Beta', clientPhone: '+55 11 98888-2222', priceWithoutFx: 10G, price: 10.5G]
            ]
        }

        when:
        Map result = service.list([sourceId: exporter.id, targetId: importer.id, clientName: 'comprador', minValue: 20, maxValue: 25, limit: 20, offset: 0])

        then:
        result.count == 1
        result.items.size() == 1
        with(result.items[0]) {
            tradeId == 'trade-1'
            wlExporterName == 'Alpha'
            wlImporterName == 'Beta'
            offerName == 'Oferta XPTO'
            exporterClientName == 'Fornecedor XPTO'
            importerClientName == 'Comprador Beta'
            exporterTotalValue == 20G
            importerTotalValue == 21.0G
            originalOfferUrl == 'https://alpha.test/troca/oferta/offer-1'
        }
    }

    void "list paginates and respects status filter"() {
        given:
        WhiteLabel exporter = new WhiteLabel(id: 'wl-exp-2', name: 'Alpha 2', gatewayUrl: 'https://alpha-2.test', contactEmail: 'alpha-2@test.com')
                .save(validate: false, flush: true)
        WhiteLabel importer = new WhiteLabel(id: 'wl-imp-2', name: 'Beta 2', gatewayUrl: 'https://beta-2.test', contactEmail: 'beta-2@test.com')
                .save(validate: false, flush: true)

        new TradeProjection(
                tradeExternalId: 'ext-2',
                tradeId: 'trade-2',
                originWhiteLabelId: exporter.id,
                targetWhiteLabelId: importer.id,
                status: 'PENDING',
                approvalMode: 'HYBRID',
                pendingReason: 'FIRST_TRADE',
                unitPrice: 5G,
                requestedQuantity: 1,
                lastEventTimestamp: new Date()
        ).save(validate: false, flush: true)

        new TradeProjection(
                tradeExternalId: 'ext-3',
                tradeId: 'trade-3',
                originWhiteLabelId: exporter.id,
                targetWhiteLabelId: importer.id,
                status: 'CONFIRMED',
                approvalMode: 'AUTO',
                unitPrice: 12G,
                confirmedQuantity: 1,
                lastEventTimestamp: new Date(System.currentTimeMillis() - 1000)
        ).save(validate: false, flush: true)

        service.tradeApprovalService = Stub(TradeApprovalService) {
            getDetails('ext-2') >> [offerName: 'Oferta pendente', exporter: [clientName: 'Fornecedor'], importer: [clientName: 'Comprador']]
        }

        when:
        Map result = service.list([status: 'PENDING', limit: 1, offset: 0])

        then:
        result.count == 1
        result.items*.tradeId == ['trade-2']
        result.items[0].pendingReason == 'FIRST_TRADE'
    }
}
