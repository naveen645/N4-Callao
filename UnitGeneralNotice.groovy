import com.navis.argo.ArgoConfig
import com.navis.argo.ArgoField
import com.navis.argo.ContextHelper
import com.navis.argo.business.atoms.FreightKindEnum
import com.navis.argo.business.atoms.UnitCategoryEnum
import com.navis.argo.business.model.CarrierVisit
import com.navis.argo.business.model.Complex
import com.navis.argo.business.model.Facility
import com.navis.argo.business.model.Operator
import com.navis.argo.webservice.types.v1_0.GenericInvokeResponseWsType
import com.navis.argo.webservice.types.v1_0.MessageType
import com.navis.argo.webservice.types.v1_0.ScopeCoordinateIdsWsType
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
import com.navis.inventory.business.imdg.HazardItem
import com.navis.inventory.business.imdg.Hazards
import com.navis.inventory.business.units.GoodsBase
import com.navis.inventory.business.units.ReeferRqmnts
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.inventory.webservice.types.v1_0.QueryResultType
import com.navis.road.RoadConfig
import com.navis.services.business.event.EventFieldChange
import com.navis.services.business.event.GroovyEvent
import com.navis.services.webservice.types.v1_0.ResponseType
import com.navis.vessel.business.schedule.VesselVisitDetails
import com.navis.www.services.argoservice.ArgoServiceLocator
import com.navis.www.services.argoservice.ArgoServicePort
import com.sun.xml.internal.ws.client.Stub
import com.navis.argo.webservice.types.v1_0.QueryResultType
import groovy.xml.MarkupBuilder
import org.apache.log4j.Level
import org.apache.log4j.Logger

/*
*
* @Author <a href="mailto:anaveen@servimostech.com"></a>, 17/June/2019
*
* Requirements : This groovy is called while updating the UnitFlexString10 based on the value  it create and delete the unit in target facility
*
* and also frame the xml and sent request to the DpwCllUnitPreadviseWSHandler groovy.
*
* @Inclusion Location : Incorporated as a code extension of the type GENERAL_NOTICE_CODE_EXTENSION. Copy -->Paste this code(DpwCllPreadviseUnitGeneralNoticeGroovy.groovy)
*
* @Set up GeneralNotice against UNIT_PROPERTY_UPDATE.
*/

class DpwCllPreadviseUnitGeneralNoticeGroovy extends AbstractGeneralNoticeCodeExtension {
    @Override
    void execute(GroovyEvent inGroovyEvent) {
        LOGGER.setLevel(Level.DEBUG)
        LOGGER.debug("PreadviseUnitCustomWsHandler started execution!!!!!!")
        Unit unit = (Unit) inGroovyEvent.getEntity()
        String unitId = unit != null ? unit.getUnitId() : null
        String inboundCv = unit.getInboundCv().getCvId()
        Facility facility = Facility.findFacility(fct_CLL)

        if (unit != null && UnitCategoryEnum.IMPORT.equals(unit.getUnitCategory()) && FreightKindEnum.FCL.equals(unit.getUnitFreightKind())) {

            String unitFlexString10 = unit != null ? unit.getUnitFlexString10() : null
            String unitCustomFlexString02 = unit.getFieldValue(UNIT_CUSTOM_DFF_FIELD)
            CarrierVisit carrierVisit = CarrierVisit.findVesselVisit(facility, inboundCv)
            VesselVisitDetails vesselVisitDetails = VesselVisitDetails.resolveVvdFromCv(carrierVisit)
            Date datevalue = vesselVisitDetails.getVvFlexDate04()

            BillOfLading srcBl
            BillOfLading masterBl
            List<HazardItem> hazardItemList = new ArrayList<>();
            ReeferRqmnts reeferRqmnts
            String tempRequiredC

            GoodsBase goodsBase = unit != null ? unit.ensureGoods() : null
            LOGGER.info("goodsBase" + goodsBase)
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

            if (unitCustomFlexString02 != null && vesselVisitDetails != null && datevalue != null) {

                for (EventFieldChange eventFieldChange : inGroovyEvent.getEvent().getFieldChanges()) {
                    LOGGER.info("eventFieldChange" + eventFieldChange)
                    if (eventFieldChange.getEvntfcMetafieldId().equalsIgnoreCase(UnitField.UNIT_FLEX_STRING10.getFieldId())) {
                        String prevVal = eventFieldChange.getPrevVal()
                        String newVal = eventFieldChange.getNewVal()
                        Complex srcComplex = Complex.findComplex(COMPLEX_CLL, ContextHelper.getThreadOperator())
                        Complex targetComplex = findComplex(COMPLEX_NEP, ContextHelper.getThreadOperator())
                        String actionId
                        String targetFacilityId = null
                        String sourceFacilityId = null
                        Facility targetFacility = null
                        Facility sourceFacility = null


                        if (prevVal.equals("3033") && unitCustomFlexString02.equals("Replicated to ARG")) {
                            //delete ufv in ARG
                            sourceFacility = findFacility(fct_ARG, targetComplex)
                            sourceFacilityId = sourceFacility.getId()
                            actionId = "CANCEL_PREADVISE"
                            if (newVal.equals("3105")) {
                                //create ufv in VNT
                                targetFacility = findFacility(fct_VNT, targetComplex)
                                targetFacilityId = targetFacility.getId()
                                unit.setFieldValue(UNIT_CUSTOM_DFF_FIELD, "Replicated to VNT")
                                actionId = "CREATE_PREADVISE"
                            }

                        } else if (prevVal.equals("3105") && unitCustomFlexString02.equals("Replicated to VNT")) {
                            //delete ufv in VNT
                            sourceFacility = findFacility(fct_VNT, targetComplex)
                            sourceFacilityId = sourceFacility.getId()
                            actionId = "CANCEL_PREADVISE"
                            if (newVal.equals("3033")) {
                                //create ufv in ARG
                                targetFacility = findFacility(fct_ARG, targetComplex)
                                targetFacilityId = targetFacility.getId()
                                unit.setFieldValue(UNIT_CUSTOM_DFF_FIELD, "Replicated to ARG")
                                actionId = "CREATE_PREADVISE"
                            }
                        } else if (prevVal.equals("3033") && !newVal.equals("3105") && unitCustomFlexString02.equals("Replicated to ARG")) {
                            //delete ufv in ARG
                            targetFacility = findFacility(fct_VNT, srcComplex)
                            sourceFacility = findFacility(fct_ARG, targetComplex)
                            sourceFacilityId = sourceFacility.getId()
                            unit.setFieldValue(UNIT_CUSTOM_DFF_FIELD, null)
                            actionId = "CANCEL_PREADVISE"
                        } else if (prevVal.equals("3105") && !newVal.equals("3033") && unitCustomFlexString02.equals("Replicated to VNT")) {
                            //delete ufv in VNT
                            targetFacility = findFacility(fct_ARG, srcComplex)
                            sourceFacility = findFacility(fct_VNT, targetComplex)
                            sourceFacilityId = sourceFacility.getId()
                            unit.setFieldValue(UNIT_CUSTOM_DFF_FIELD, null)
                            actionId = "CANCEL_PREADVISE"
                        } else {
                            if (newVal.equals("3033")) {
                                targetFacility = findFacility(fct_ARG, targetComplex)
                                targetFacilityId = targetFacility.getId()
                                unit.setFieldValue(UNIT_CUSTOM_DFF_FIELD, "Replicated to ARG")
                                actionId = "CREATE_PREADVISE"


                            } else if (newVal.equals("3105")) {
                                targetFacility = findFacility(fct_VNT, targetComplex)
                                targetFacilityId = targetFacility.getId()
                                unit.setFieldValue(UNIT_CUSTOM_DFF_FIELD, "Replicated to VNT")
                                actionId = "CREATE_PREADVISE"

                            }

                        }

                        StringWriter strWriter = new StringWriter();
                        strWriter.write('<custom class="DpwCLLCreateOrCancelPreadviseUnitWsHandler" type="extension">\n');
                        MarkupBuilder markUpBuilder = new MarkupBuilder(strWriter);
                        markUpBuilder.setDoubleQuotes(true);
                        markUpBuilder.'units'() {
                            markUpBuilder.'unit'() {
                                markUpBuilder.'requestType'("CREATE_PREADVISE")
                                markUpBuilder.'targetFacility'(targetFacilityId)
                                markUpBuilder.'unitId'(unit.getUnitId())
                                markUpBuilder.'category'(unit.getUnitCategory())
                                markUpBuilder.'ibActualVisit'(unit.getUnitActiveUfvNowActive().getUfvActualIbCv())
                                markUpBuilder.'LineOp'(unit.getUnitLineOperator())
                                markUpBuilder.'freightKind'(unit.getUnitFreightKind())
                                markUpBuilder.'loadPort'(unit.getUnitRouting().getRtgPOL())
                                markUpBuilder.'dischargePort'(unit.getUnitRouting().getRtgPOD1())
                                markUpBuilder.'grossWeightKg'(unit.getUnitGoodsAndCtrWtKg())
                                markUpBuilder.'hazards'() {
                                    for (int i = 0; i < hazardItemList.size(); i++) {
                                        String imdgClass = hazardItemList.get(i).getHzrdiImdgCode().getKey()
                                        String uNnum = hazardItemList.get(i).getHzrdiUNnum()
                                        markUpBuilder.'hazard'('imdgCode': imdgClass, 'unNbr': uNnum) {
                                        }
                                    }
                            }
                                if(tempRequiredC!=null){
                                    markUpBuilder.'reefer'('reqTemp':tempRequiredC , 'tempUnit': "C")
                                }
                                if (srcBl != null) {
                                    markUpBuilder.'billOfLading'() {
                                        markUpBuilder.'blNbr'(srcBl.getBlNbr())
                                        markUpBuilder.'blLineOp'(srcBl.getBlLineOperator())
                                        markUpBuilder.'blcarrierVisit'(srcBl.getBlCarrierVisit())
                                        markUpBuilder.'blPol'(srcBl.getBlPol())
                                        markUpBuilder.'blPod'(srcBl.getBlPod1())
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
                            markUpBuilder.'unit'() {
                                markUpBuilder.'unitId'(unitId)
                                markUpBuilder.'targetFacility'(sourceFacilityId)
                                markUpBuilder.'unitAction'("CANCEL_PREADVISE")
                            }


                        }
                        strWriter.write('</custom>')
                        String inXml = strWriter.toString()
                        String request = sendN4WSGroovyRequest(inXml, sourceFacility, targetFacility)
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

    public String sendN4WSGroovyRequest(String xmlString, Facility sourceFacility, Facility targetFacility) throws BizViolation {
        ArgoServiceLocator locator = new ArgoServiceLocator();
        String n4Url = ArgoConfig.N4_WS_ARGO_URL.getSetting(ContextHelper.getThreadUserContext());
        ArgoServicePort port = locator.getArgoServicePort(new URL(n4Url));
        LOGGER.info("port::" + port)
        port._setProperty(Stub.USERNAME_PROPERTY, RoadConfig.GOS_WS_USERNAME.getSetting(ContextHelper.getThreadUserContext()));
        port._setProperty(Stub.PASSWORD_PROPERTY, RoadConfig.GOS_WS_PASSWORD.getSetting(ContextHelper.getThreadUserContext()));

        ScopeCoordinateIdsWsType scopeCoordinates = this.getScopeCoordenatesForWs(sourceFacility, targetFacility)
//Scope Cordinates
        LOGGER.debug("Current Scop cordniates :: " + scopeCoordinates.getComplexId())
        LOGGER.debug("Current Scop cordniates :: " + scopeCoordinates.getFacilityId())
        GenericInvokeResponseWsType invokeResponseWsType = port.genericInvoke(scopeCoordinates, xmlString);
        com.navis.argo.webservice.types.v1_0.ResponseType response = invokeResponseWsType.getCommonResponse();
        QueryResultType[] queryResultTypes = response.getQueryResults();

        String responseString = "";
        MessageType[] msgType = response.getMessageCollector().getMessages();

        if (queryResultTypes) {
            responseString = queryResultTypes.getAt(0).getResult();
        }
        return responseString;
    }

    public ScopeCoordinateIdsWsType getScopeCoordenatesForWs(Facility sourceFacility, Facility targetFacility) {
        ScopeCoordinateIdsWsType scopeCoordinates = new ScopeCoordinateIdsWsType();
        scopeCoordinates.setOperatorId(ContextHelper.getThreadOperator().getId());
        scopeCoordinates.setComplexId(sourceFacility.getFcyComplex().getCpxId());
        scopeCoordinates.setFacilityId(sourceFacility.getFcyId());
        scopeCoordinates.setYardId(sourceFacility.getActiveYard().getYrdId());

        scopeCoordinates.setComplexId(targetFacility.getFcyComplex().getCpxId());
        scopeCoordinates.setFacilityId(targetFacility.getFcyId());
        scopeCoordinates.setYardId(targetFacility.getActiveYard().getYrdId());

        return scopeCoordinates;
    }

    private final static String COMPLEX_NEP = "NEP"
    private final static String COMPLEX_CLL = "CLL"
    private final static String fct_ARG = "ARG"
    private final static String fct_VNT = "VNT"
    private final static String fct_CLL = "CLL"

    private static final Logger LOGGER = Logger.getLogger(DpwCllPreadviseUnitGeneralNoticeGroovy.class)
    private
    static MetafieldId UNIT_CUSTOM_DFF_FIELD = MetafieldIdFactory.valueOf("customFlexFields.unitCustomDFFlexString02")
    private static MetafieldId BL_CUSTOM_DFF_TYPE = MetafieldIdFactory.valueOf("customFlexFields.blCustomDFFtype")
    private static MetafieldId BL_CUSTOM_DFF_CARGA = MetafieldIdFactory.valueOf("customFlexFields.blCustomDFFCarga"

}
