import com.navis.argo.ArgoConfig
import com.navis.argo.ArgoField
import com.navis.argo.ContextHelper
import com.navis.argo.business.atoms.CarrierVisitPhaseEnum
import com.navis.argo.business.atoms.UnitCategoryEnum
import com.navis.argo.business.model.CarrierVisit
import com.navis.argo.business.model.Complex
import com.navis.argo.business.model.Facility
import com.navis.argo.business.model.Operator
import com.navis.argo.webservice.types.v1_0.GenericInvokeResponseWsType
import com.navis.argo.webservice.types.v1_0.MessageType
import com.navis.argo.webservice.types.v1_0.ScopeCoordinateIdsWsType
import com.navis.cargo.InventoryCargoField
import com.navis.cargo.business.model.BillOfLading
import com.navis.cargo.business.model.BlGoodsBl
import com.navis.cargo.business.model.GoodsBl
import com.navis.external.services.AbstractGeneralNoticeCodeExtension
import com.navis.framework.metafields.MetafieldId
import com.navis.framework.metafields.MetafieldIdFactory
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.framework.util.BizViolation
import com.navis.inventory.business.api.UnitField
import com.navis.inventory.business.atoms.UfvTransitStateEnum
import com.navis.inventory.business.atoms.UnitVisitStateEnum
import com.navis.inventory.business.imdg.HazardItem
import com.navis.inventory.business.imdg.Hazards
import com.navis.inventory.business.imdg.ImdgClass
import com.navis.inventory.business.units.GoodsBase
import com.navis.inventory.business.units.ReeferRqmnts
import com.navis.inventory.business.units.Unit
import com.navis.argo.webservice.types.v1_0.QueryResultType
import com.navis.road.RoadConfig
import com.navis.services.business.event.EventFieldChange
import com.navis.services.business.event.GroovyEvent
import com.navis.vessel.api.VesselVisitField
import com.navis.vessel.business.schedule.VesselVisitDetails
import com.navis.www.services.argoservice.ArgoServiceLocator
import com.navis.www.services.argoservice.ArgoServicePort
import com.navis.argo.webservice.types.v1_0.ResponseType
import groovy.xml.MarkupBuilder
import org.apache.axis.client.Stub
import org.apache.log4j.Level
import org.apache.log4j.Logger


/*
*
* @Author <a href="mailto:anaveen@servimostech.com"></a>, 17/June/2019
*
* Requirements : This groovy is called while updating the vvflexDate04 based on the value of unitFlexString10 in unit, it create the unit in target facility
*
* and also frame the xml and sent request to the DpwCllUnitPreadviseWSHandler groovy.
*
* @Inclusion Location : Incorporated as a code extension of the type GENERAL_NOTICE_CODE_EXTENSION. Copy -->Paste this code(DpwCllVesselVisitGeneralNotice.groovy)
*
* @Set up GeneralNotice against UPDATE_VV.
*/

class DpwCllVesselVisitGeneralNotice extends AbstractGeneralNoticeCodeExtension {
    @Override
    void execute(GroovyEvent inGroovyEvent) {
        LOGGER.setLevel(Level.DEBUG)
        LOGGER.info("VesselVisitGeneralNotice started execution!!!!!!!")
        VesselVisitDetails vesselVisitDetails = (VesselVisitDetails) inGroovyEvent.getEntity()
        CarrierVisit carrierVisit = vesselVisitDetails != null ? vesselVisitDetails.getCvdCv() : null
        String cvId = carrierVisit != null ? carrierVisit.getCvId() : null

        CarrierVisitPhaseEnum visitPhaseEnum = vesselVisitDetails.getVvdVisitPhase()
        if (VesselVisitDetails != null && (CarrierVisitPhaseEnum.INBOUND.equals(visitPhaseEnum) || CarrierVisitPhaseEnum.WORKING.equals(visitPhaseEnum) || CarrierVisitPhaseEnum.ARRIVED.equals(visitPhaseEnum) || CarrierVisitPhaseEnum.DEPARTED.equals(visitPhaseEnum))) {

            for (EventFieldChange eventFieldChange : inGroovyEvent.getEvent().getFieldChanges()) {

                if (eventFieldChange.getEvntfcMetafieldId().equalsIgnoreCase(VesselVisitField.VV_FLEX_DATE04.getFieldId())) {

                    DomainQuery units = QueryUtils.createDomainQuery("Unit")
                            .addDqPredicate(PredicateFactory.eq(id2, cvId))
                            .addDqPredicate(PredicateFactory.eq(UnitField.UNIT_VISIT_STATE, UnitVisitStateEnum.ACTIVE))
                            .addDqPredicate(PredicateFactory.eq(UnitField.UNIT_CATEGORY, UnitCategoryEnum.IMPORT))
                            .addDqPredicate(PredicateFactory.eq(UFV_TRANSIT_STATE, UfvTransitStateEnum.S20_INBOUND))
                            .addDqPredicate(PredicateFactory.isNotNull(UnitField.UNIT_FLEX_STRING10))
                    LOGGER.info("units"+units)
                    List unitList = HibernateApi.getInstance().findEntitiesByDomainQuery(units)
                    LOGGER.info("unitList"+unitList)
                    for (Unit unit : unitList) {
                        LOGGER.info("unit"+unit)
                        String unitId = unit != null ? unit.getUnitId() : null
                        LOGGER.info("unitId"+unitId)
                        BillOfLading srcBl
                        BillOfLading masterBl
                        List<HazardItem> hazardItemList = new ArrayList<>();
                        ReeferRqmnts reeferRqmnts
                        String tempRequiredC

                        GoodsBase goodsBase = unit != null ? unit.ensureGoods() : null
                        LOGGER.info("goodsBase"+goodsBase)
                        if (goodsBase != null) {
                            Hazards gdsHazards = goodsBase.getGdsHazards()
                            if (gdsHazards != null) {
                                hazardItemList = gdsHazards.getHzrdItems()
                            }

                            if (reeferRqmnts != null) {

                                tempRequiredC = reeferRqmnts.getRfreqTempRequiredC()
                            }


                            GoodsBl goodsBl = GoodsBl.resolveGoodsBlFromGoodsBase(goodsBase)
                            if (goodsBl != null) {
                                Set goodsBlSet = goodsBl.getGdsblBlGoodsBls()

                                for (BlGoodsBl blGoodsBl : goodsBlSet) {
                                    srcBl = blGoodsBl.getBlgdsblBl()
                                }
                            }

                            String unitBlMaster = unit.getFieldValue(UNIT_CUSTOM_DFF_FIELD)
                            if (unitBlMaster != null) {
                                masterBl = BillOfLading.findBillOfLading(unitBlMaster, unit.getUnitLineOperator(), unit.getUnitActiveUfvNowActive().getInboundCarrierVisit())

                            }
                        }

                        if (unitId != null && UnitCategoryEnum.IMPORT.equals(unit.getUnitCategory())) {
                            LOGGER.info("unitId is not null coming inside !!!!!!!!!!!!")
                            String unitFlexString10 = unit != null ? unit.getUnitFlexString10() : null
                            LOGGER.info("unitFlexString10"+unitFlexString10)
                            Facility targetFacility = null
                            String targetFacilityId = null
                            Complex trgtComplex = findComplex("NEP", ContextHelper.getThreadOperator())
                            LOGGER.info("trgtComplex"+trgtComplex)
                            if (unitFlexString10.equals("3033")) {
                                LOGGER.info("unitFlexString10 value is 3033 hence coming else condition")
                                targetFacility = findFacility("ARG", trgtComplex)
                                targetFacilityId = targetFacility.getId()

                            } else {
                                LOGGER.info("unitFlexString10 value is 3015 hence coming else condition")
                                targetFacility = findFacility("VNT", trgtComplex)
                                targetFacilityId = targetFacility.getId()
                            }

                            StringWriter strWriter = new StringWriter();
                            strWriter.write('<custom class="DpwCLLCreateOrCancelPreadviseUnitWsHandler" type="extension">\n');
                            MarkupBuilder markUpBuilder = new MarkupBuilder(strWriter);
                            markUpBuilder.setDoubleQuotes(true);
                            markUpBuilder.'units'() {
                                for (int j = 0; j < unitList.size(); j++) {
                                    markUpBuilder.'unit'() {
                                        markUpBuilder.'requestType'("CREATE_PREADVISE")
                                        markUpBuilder.'targetFacility'(targetFacilityId)
                                        markUpBuilder.'unitId'(unit.getUnitId())
                                        markUpBuilder.'isoCode'(unit.getPrimaryEq().getEqEquipType().getEqtypId())
                                        markUpBuilder.'category'(unit.getUnitCategory().getKey())
                                        markUpBuilder.'ibActualVisit'(unit.getUnitActiveUfvNowActive().getInboundCarrierVisit())
                                        markUpBuilder.'LineOp'(unit.getUnitLineOperator().getBzuId())
                                        markUpBuilder.'freightKind'(unit.getUnitFreightKind().getKey())
                                        markUpBuilder.'loadPort'(unit.getUnitRouting().getRtgPOL().getPointId())
                                        markUpBuilder.'dischargePort'(unit.getUnitRouting().getRtgPOD1().getPointId())
                                        markUpBuilder.'grossWeightKg'(unit.getUnitGoodsAndCtrWtKg())
                                        if (hazardItemList != null && hazardItemList.size() > 0) {
                                            markUpBuilder.'hazards'() {
                                                for (int i = 0; i < hazardItemList.size(); i++) {
                                                    String imdgClass = hazardItemList.get(i).getHzrdiImdgCode().getKey()
                                                    String uNnum = hazardItemList.get(i).getHzrdiUNnum()
                                                    markUpBuilder.'hazard'('imdgCode': imdgClass, 'unNbr': uNnum) {
                                                    }
                                                }
                                            }
                                        }
                                        if (tempRequiredC != null) {
                                            markUpBuilder.'reefer'('reqTemp': tempRequiredC, 'tempUnit': "C")
                                        }

                                        if (srcBl != null) {
                                            markUpBuilder.'billOfLading'() {
                                                markUpBuilder.'blNbr'(srcBl.getBlNbr())
                                                markUpBuilder.'blLineOp'(srcBl.getBlLineOperator().getBzuId())
                                                markUpBuilder.'blcarrierVisit'(srcBl.getBlCarrierVisit())
                                                markUpBuilder.'blPol'(srcBl.getBlPol().getPointId())
                                                markUpBuilder.'blPod'(srcBl.getBlPod1().getPointId())
                                                markUpBuilder.'blManifestedQty'(srcBl.getBlManifestedQty())
                                                markUpBuilder.'blType'(srcBl.getFieldValue(BL_CUSTOM_DFF_TYPE))
                                                markUpBuilder.'blCarga'(srcBl.getFieldValue(BL_CUSTOM_DFF_CARGA))
                                                markUpBuilder.'blOrigin'(srcBl.getBlOrigin())
                                                if (masterBl != null) {
                                                    markUpBuilder.'blMaster'() {
                                                        markUpBuilder.'mblNbr'(masterBl.getBlNbr())
                                                        markUpBuilder.'mblLineOp'(masterBl.getBlLineOperator().getBzuId())
                                                        markUpBuilder.'mblcarrierVisit'(masterBl.getBlCarrierVisit())
                                                        markUpBuilder.'mblPol'(masterBl.getBlPol().getPrimaryKey())
                                                        markUpBuilder.'mblPod'(masterBl.getBlPod1().getPrimaryKey())
                                                        markUpBuilder.'mblManifestedQty'(masterBl.getBlManifestedQty())
                                                        markUpBuilder.'mblType'(masterBl.getFieldValue(BL_CUSTOM_DFF_TYPE))
                                                        markUpBuilder.'mblCarga'(masterBl..getFieldValue(BL_CUSTOM_DFF_CARGA))
                                                    }
                                                }
                                            }
                                        }
                                    }

                                }

                            }
                            strWriter.write('</custom>')
                            String inXml = strWriter.toString()
                            LOGGER.debug("inXml::"+inXml)
                            String request = sendN4WSGroovyRequest(inXml, targetFacility)
                            LOGGER.debug("request::"+request)
                        }
                    }
                }
            }
        }
    }

    static Complex findComplex(String inCpxId, Operator inOperator) {
        DomainQuery dq = QueryUtils.createDomainQuery("Complex").addDqPredicate(PredicateFactory.eq(ArgoField.CPX_ID, inCpxId)).addDqPredicate(PredicateFactory.eq(ArgoField.CPX_OPERATOR, inOperator.getPrimaryKey()));
        dq.setScopingEnabled(false)
        dq.setBypassInstanceSecurity(false)
        return (Complex) HibernateApi.getInstance().getUniqueEntityByDomainQuery(dq);
    }

    static Facility findFacility(String inFcyId, Complex inComplex) {
        DomainQuery dq = QueryUtils.createDomainQuery("Facility").addDqPredicate(PredicateFactory.eq(ArgoField.FCY_ID, inFcyId)).addDqPredicate(PredicateFactory.eq(ArgoField.FCY_COMPLEX, inComplex.getCpxGkey()));
        dq.setScopingEnabled(false);
        return (Facility) HibernateApi.getInstance().getUniqueEntityByDomainQuery(dq);
    }

    public String sendN4WSGroovyRequest(String xmlString, Facility facility) throws BizViolation {
        ArgoServiceLocator locator = new ArgoServiceLocator();
        String n4Url = ArgoConfig.N4_WS_ARGO_URL.getSetting(ContextHelper.getThreadUserContext());
        ArgoServicePort port = locator.getArgoServicePort(new URL(n4Url));
        LOGGER.info("port::" + port)

        port._setProperty(Stub.USERNAME_PROPERTY, RoadConfig.GOS_WS_USERNAME.getSetting(ContextHelper.getThreadUserContext()));
        port._setProperty(Stub.PASSWORD_PROPERTY, RoadConfig.GOS_WS_PASSWORD.getSetting(ContextHelper.getThreadUserContext()));

        ScopeCoordinateIdsWsType scopeCoordinates = this.getScopeCoordenatesForWs(facility)
        GenericInvokeResponseWsType invokeResponseWsType = port.genericInvoke(scopeCoordinates, xmlString);
        ResponseType response = invokeResponseWsType.getCommonResponse();
        QueryResultType[] queryResultTypes = response.getQueryResults();
        String responseString = "";
        MessageType[] msgType = response.getMessageCollector().getMessages();

        if (queryResultTypes) {
            responseString = queryResultTypes.getAt(0).getResult();
        }
        return responseString;
    }

    public ScopeCoordinateIdsWsType getScopeCoordenatesForWs(Facility facility) {
        ScopeCoordinateIdsWsType scopeCoordinates = new ScopeCoordinateIdsWsType();
        scopeCoordinates.setOperatorId(ContextHelper.getThreadOperator().getId());
        scopeCoordinates.setComplexId(facility.getFcyComplex().getCpxId());
        scopeCoordinates.setFacilityId(facility.getFcyId());
        scopeCoordinates.setYardId(facility.getActiveYard().getYrdId());


        return scopeCoordinates;
    }
    private static MetafieldId id2 = MetafieldIdFactory.valueOf("unitDeclaredIbCv.cvId")
    private static MetafieldId UNIT_CUSTOM_DFF_FIELD = MetafieldIdFactory.valueOf("customFlexFields.unitCustomDFFblMaster")
    private static MetafieldId BL_CUSTOM_DFF_TYPE = MetafieldIdFactory.valueOf("customFlexFields.blCustomDFFtype")
    private static MetafieldId BL_CUSTOM_DFF_CARGA = MetafieldIdFactory.valueOf("customFlexFields.blCustomDFFCarga")
    private static MetafieldId UFV_TRANSIT_STATE = MetafieldIdFactory.valueOf("unitActiveUfv.ufvTransitState")
    private static MetafieldId BL_CV = MetafieldIdFactory.valueOf("blCarrierVisit.cvId")
    private final static Logger LOGGER = Logger.getLogger(DpwCllVesselVisitGeneralNotice.class)
}

