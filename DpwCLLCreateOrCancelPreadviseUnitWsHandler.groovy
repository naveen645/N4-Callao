import com.navis.argo.ArgoField
import com.navis.argo.business.atoms.DataSourceEnum
import com.navis.argo.business.atoms.FreightKindEnum
import com.navis.argo.business.atoms.LocTypeEnum
import com.navis.argo.business.atoms.UnitCategoryEnum
import com.navis.argo.business.model.CarrierVisit
import com.navis.argo.business.model.Complex
import com.navis.argo.business.model.Facility
import com.navis.argo.business.model.Operator
import com.navis.argo.business.reference.EquipType
import com.navis.argo.business.reference.Equipment
import com.navis.argo.business.reference.RoutingPoint
import com.navis.argo.business.reference.ScopedBizUnit
import com.navis.cargo.CargoLotFields
import com.navis.cargo.business.api.BillOfLadingManager
import com.navis.cargo.business.model.BillOfLading
import com.navis.external.argo.AbstractArgoCustomWSHandler
import com.navis.framework.business.Roastery
import com.navis.framework.metafields.MetafieldIdFactory
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.UserContext
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.framework.util.BizViolation
import com.navis.framework.util.message.MessageCollector
import com.navis.inventory.InvField
import com.navis.inventory.business.api.UnitField
import com.navis.inventory.business.api.UnitFinder
import com.navis.inventory.business.api.UnitManager
import com.navis.inventory.business.imdg.Hazards
import com.navis.inventory.business.units.Goods
import com.navis.inventory.business.units.GoodsBase
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitFacilityVisit
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.jdom.*
import org.jdom.Element
import org.jdom.input.SAXBuilder

/*


<custom class="DpwCLLCreateOrCancelPreadviseUnitWsHandler" type="extension">
	<units>
		<unit>
			<requestType>CREATE_PREADVISE/CANCEL_PREADVISE</requestType>
			<targetFacility>ARG</targetFacility>
			<unitId>TEST8641975</unitId>
			<isoCode>22G1</isoCode>
			<category>IMPRT/STRGE/TRSHP</category>
			<ibActualVisit>ABC001</ibActualVisit>
			<LineOp>LINE</LineOp>
			<freightKind>FCL/LCL/MTY</freightKind>
			<loadPort>POL</loadPort>
			<dischargePort>POD</dischargePort>
			<grossWeightKg>15230</grossWeightKg>
			<hazards>
				<hazard imdgCode="1" unNbr="1234" />
				<hazard imdgCode="2" unNbr="6523" />
			</hazards>
			<reefer reqTemp="12.0" tempUnit="C" />
			<billOfLading>
				<blNbr>BL0001</blNbr>
				<blLineOp>LINE</blLineOp> <!-- can be used from unit details -->
				<blcarrierVisit>ABC001</blcarrierVisit> <!-- can be used from unit details -->
				<blPol>POL</blPol> <!-- can be used from unit details -->
				<blPod>POD</blPod> <!-- can be used from unit details -->
				<blManifestedQty></blManifestedQty>
				<blType></blType>
				<blCarga></blCarga>
				<blOrigin></blOrigin>
				<blMaster>
					<mblNbr>MBL0001</mblNbr>
					<mblLineOp>LINE</mblLineOp> <!-- can be used from unit details -->
					<mblcarrierVisit>ABC001</mblcarrierVisit> <!-- can be used from unit details -->
					<mblPol>POL</mblPol> <!-- can be used from unit details -->
					<mblPod>POD</mblPod> <!-- can be used from unit details -->
					<mblManifestedQty></mblManifestedQty>
					<mblType></mblType>
					<mblCarga></mblCarga>
				</blMaster>
			</billOfLading>
		</unit>
	</units>
</custom>


*/


class DpwCLLCreateOrCancelPreadviseUnitWsHandler extends AbstractArgoCustomWSHandler {
    @Override
    void execute(UserContext userContext, MessageCollector messageCollector, Element inWsRequest, Element wsInResponse, Long inWslogGkey) {
        LOGGER.setLevel(Level.DEBUG)
        LOGGER.debug("DpwCLLCreateOrCancelPreadviseUnitWsHandler started execution!!!!!!")
        LOGGER.debug("XML file:: $inWsRequest")//Xml message
        /*
        LOGGER.debug("userContext" + userContext)
        LOGGER.debug("messageCollector" + messageCollector)
        LOGGER.debug("inWsRequest" + inWsRequest)
        LOGGER.debug("wsInResponse" + wsInResponse)
        LOGGER.debug("inWslogGkey" + inWslogGkey) */
        Element rootNode = inWsRequest.getChild("units")
        List childNodes = rootNode.getChildren("unit")
        String responseMsg = "<units>";
        Iterator iterator = childNodes.iterator()
        while (iterator.hasNext()) {

            Element childNode = (Element) iterator.next()

            String requestType, targetFacility, loadPort, dischargePort, imdgCode, unNbr, reqTemp, tempUnit;
            String blNbr, blLineOp, blcarrierVisit, blPol, blPod, blManifestedQty, blType,
                   blCarga, blOrigin;
            String mblNbr, mblLineOp, mblcarrierVisit, mblPol, mblPod, mblManifestedQty,
                   mblType, mblCarga;


            requestType = childNode.getChild("requestType") != null ? childNode.getChild("requestType").getValue() :
                    null
            targetFacility = childNode.getChild("targetFacility") != null ? childNode.getChild("targetFacility")
                    .getValue() : null
            String unitId = childNode.getChild("unitId") != null ? childNode.getChild("unitId").getValue() : null
            String isoCode = childNode.getChild("isoCode") != null ? childNode.getChild("isoCode").getValue() : null
            String unitCategory = childNode.getChild("category") != null ? childNode.getChild("category").getValue() :
                    null
            String ibActualVisit = childNode.getChild("ibActualVisit") != null ? childNode.getChild("ibActualVisit")
                    .getValue() : null
            String LineOp = childNode.getChild("LineOp") != null ? childNode.getChild("LineOp").getValue() : null
            String freightKind = childNode.getChild("freightKind") != null ? childNode.getChild("freightKind")
                    .getValue() :
                    null
            loadPort = childNode.getChild("loadPort") != null ? childNode.getChild("loadPort").getValue() : null
            dischargePort = childNode.getChild("dischargePort") != null ? childNode.getChild("dischargePort")
                    .getValue() : null
            String grossWeightKg = childNode.getChild("grossWeightKg") != null ? childNode.getChild("grossWeightKg")
                    .getValue() : null

            Element hazardsNode = childNode.getChild("hazards")
            LOGGER.debug("*** hazardsNode : " + hazardsNode)

            List hazardNodes = hazardsNode.getChildren("hazard")
            LOGGER.debug("*** hazardNodes : " + hazardNodes)
            Iterator hazIterator = hazardNodes.iterator()

            Boolean hasReeferReq = false, isReeferType = false, invalidReef = false
            if (childNode.getChild("reefer") != null) {
                reqTemp = childNode.getChild("reefer").getAttribute("reqTemp") != null ? childNode.getChild("reefer")
                        .getAttribute("reqTemp").getValue() : null
                tempUnit = childNode.getChild("reefer").getAttribute("tempUnit") != null ?
                        childNode.getChild("reefer").getAttribute("tempUnit").getValue() : null
               /* if (reqTemp != null && reqTemp.length() > 0 && (tempUnit.equalsIgnoreCase("c") || tempUnit
                        .equalsIgnoreCase("f")))
                {
                    hasReeferReq = true
                }
                else
                {
                    LOGGER.debug("Invalid Reefer requirements")
                    responseMsg = responseMsg.concat(createResponse(unitId, requestType, targetFacility, "FAILURE",
                            "Invalid Reefer requirements!!!"))
                    continue
                }*/
            }




            Element blNode = childNode.getChild("billOfLading")
            if (blNode != null) {
                blNbr = blNode.getChild("blNbr") != null ? blNode.getChild("blNbr").getValue() : null
                blLineOp = blNode.getChild("blLineOp") != null ? blNode.getChild("blLineOp").getValue() : null

                blcarrierVisit = blNode.getChild("blcarrierVisit") != null ? blNode.getChild("blcarrierVisit")
                        .getValue() : null
                blPol = blNode.getChild("blPol") != null ? blNode.getChild("blPol").getValue() : null
                blPod = blNode.getChild("blPod") != null ? blNode.getChild("blPod").getValue() : null
                blManifestedQty = blNode.getChild("blManifestedQty") != null ? blNode.getChild("blManifestedQty")
                        .getValue() : null
                blType = blNode.getChild("blType") != null ? blNode.getChild("blType").getValue() : null
                blCarga = blNode.getChild("blCarga") != null ? blNode.getChild("blCarga").getValue() : null
                blOrigin = blNode.getChild("blOrigin") != null ? blNode.getChild("blOrigin").getValue() : null
                Element mblNode = blNode.getChild("blMaster")
                if (mblNode != null) {
                    mblNbr = mblNode.getChild("mblNbr") != null ? mblNode.getChild("mblNbr").getValue() : null
                    mblLineOp = mblNode.getChild("mblLineOp") != null ? mblNode.getChild("mblLineOp").getValue() : null

                    mblcarrierVisit = mblNode.getChild("mblcarrierVisit") != null ? mblNode.getChild("mblcarrierVisit")
                            .getValue() : null
                    mblPol = mblNode.getChild("mblPol") != null ? mblNode.getChild("mblPol").getValue() : null
                    mblPod = mblNode.getChild("mblPod") != null ? mblNode.getChild("mblPod").getValue() : null
                    mblManifestedQty = mblNode.getChild("mblManifestedQty") != null ? mblNode
                            .getChild("mblManifestedQty").getValue() : null
                    mblType = mblNode.getChild("mblType") != null ? mblNode.getChild("mblType").getValue() : null
                    mblCarga = mblNode.getChild("mblCarga") != null ? mblNode.getChild("mblCarga").getValue() : null

                }

            }

            LOGGER.debug("*** Unit Details ***")
            LOGGER.debug("requestType : " + requestType)
            LOGGER.debug("targetFacility : " + targetFacility)
            LOGGER.debug("unitId : " + unitId)
            LOGGER.debug("isoCode : " + isoCode)
            LOGGER.debug("unitCategory : " + unitCategory)
            LOGGER.debug("ibActualVisit : " + ibActualVisit)
            LOGGER.debug("LineOp : " + LineOp)
            LOGGER.debug("freightKind : " + freightKind)
            LOGGER.debug("loadPort : " + loadPort)
            LOGGER.debug("dischargePort : " + dischargePort)
            LOGGER.debug("imdgCode : " + imdgCode)
            LOGGER.debug("unNbr : " + unNbr)
            LOGGER.debug("reqTemp : " + reqTemp)
            LOGGER.debug("tempUnit : " + tempUnit)

            LOGGER.debug("*** Bl Details ***")
            LOGGER.debug("blNbr : " + blNbr)
            LOGGER.debug("blLineOp : " + blLineOp)
            LOGGER.debug("blcarrierVisit : " + blcarrierVisit)
            LOGGER.debug("blPol : " + blPol)
            LOGGER.debug("blPod : " + blPod)
            LOGGER.debug("blManifestedQty : " + blManifestedQty)
            LOGGER.debug("blType : " + blType)
            LOGGER.debug("blCarga : " + blCarga)
            LOGGER.debug("blOrigin : " + blOrigin)

            LOGGER.debug("*** MBL Details ***")
            LOGGER.debug("mblNbr : " + mblNbr)
            LOGGER.debug("mblLineOp : " + mblLineOp)
            LOGGER.debug("mblcarrierVisit : " + mblcarrierVisit)
            LOGGER.debug("mblPol : " + mblPol)
            LOGGER.debug("mblPod : " + mblPod)
            LOGGER.debug("mblManifestedQty : " + mblManifestedQty)
            LOGGER.debug("mblType : " + mblType)
            LOGGER.debug("mblCarga : " + mblCarga)


            Facility targetFcy = targetFacility != null ? findFacility(targetFacility) : null
            Complex targetCmplx = targetFcy != null ? targetFcy.getFcyComplex() : null

            LOGGER.debug("targetFcy : " + targetFcy)
            boolean checkFlag=false


            String blResponse = null, mblResponse = null

            if (targetFcy == null) {
                LOGGER.debug("Target facility unknown")
                responseMsg = responseMsg.concat(createResponse(unitId, requestType, targetFacility, "FAILURE",
                        "Invalid Facility requested!!!"))
                continue
            }

            if (!REQ_PREADVISE.equalsIgnoreCase(requestType) && !CANCEL_PREADVISE.equalsIgnoreCase(requestType)) {
                LOGGER.debug("Invalid request type")
                responseMsg = responseMsg.concat(createResponse(unitId, requestType, targetFacility, "FAILURE",
                        "Invalid Request Type!!!"))
                continue
            }

            unitId = (unitId != null && unitId.length() > 0) ? unitId : null
            if (unitId == null) {
                LOGGER.debug("Invalid Container number requested")
                responseMsg = responseMsg.concat(createResponse(unitId, requestType, targetFacility, "FAILURE",
                        "Invalid Container number requested!!!"))
                continue
            }
            isoCode = (isoCode != null && isoCode.length() > 0) ? isoCode : "UNKN"
            EquipType isoType = EquipType.findEquipType(isoCode)


            UnitFinder unitFinder = (UnitFinder) Roastery.getBean(UnitFinder.BEAN_ID)

            Equipment equipment = (unitId != null && unitId.length() > 0) ? Equipment.findEquipment(unitId) : null


            isReeferType = equipment != null ? equipment.getEqEquipType().temperatureControlled : false
            isReeferType = (equipment == null && isoType != null) ? isoType.temperatureControlled : false

          /*  if (hasReeferReq && !isReeferType)
            {
                LOGGER.debug("Reefer requirements provided for a non-reefer container")
                responseMsg = responseMsg.concat(createResponse(unitId, requestType, targetFacility, "FAILURE",
                        "Reefer requirements provided for a non-reefer type container!!!"))
                continue
            }*/
            Unit targetUnit = equipment != null ? unitFinder.findActiveUnit(targetCmplx, equipment) : null
            UnitFacilityVisit targetUfv = targetUnit != null ? targetUnit.getUnitActiveUfvNowActive() : null


            UnitManager unitManager = (UnitManager) Roastery.getBean(UnitManager.BEAN_ID)
            if (REQ_PREADVISE.equalsIgnoreCase(requestType) && unitId != null) {
                if (targetUfv != null) {
                    LOGGER.debug("Unit already available in target facility")
                    responseMsg = responseMsg.concat(createResponse(unitId, requestType, targetFacility, "FAILURE",
                            "Requested unit already available!!!"))
                } else {
                    Boolean hasMbl = false
                    BillOfLading targetMbl = null, targetBl = null
                    try{
                        if (mblNbr != null && mblNbr.length() > 0) {
                            hasMbl = true

                            CarrierVisit mblIbCv = (mblcarrierVisit != null && mblcarrierVisit.length() > 0) ?
                                    findCarrierVisit(targetCmplx, targetFcy, mblcarrierVisit) : null
                            LOGGER.debug("Requested IB CV : " + mblcarrierVisit)
                            LOGGER.debug("N4 IB CV : " + mblIbCv)
                            ScopedBizUnit mblOp = (mblLineOp != null && mblLineOp.length() > 0) ? ScopedBizUnit
                                    .findEquipmentOperator(mblLineOp) : null
                            LOGGER.debug("Requested Line Op : " + mblLineOp)
                            LOGGER.debug("N4 Line Op : " + mblOp)

                            if (mblIbCv == null || mblLineOp == null) {
                                LOGGER.debug("Unable to find/create MBL: Invalid Carrier Visit/Line Op/Category")
                                responseMsg = responseMsg.concat(createResponse(unitId, requestType, targetFacility, "FAILURE", "Unable to find/create MBL: Invalid Carrier Visit/Line Op/Category!!!"))
                                continue
                            }
                            RoutingPoint mblLoadPort = RoutingPoint.findRoutingPoint(mblPol)
                            RoutingPoint mblDischPort = RoutingPoint.findRoutingPoint(mblPod)

                            targetMbl = BillOfLading.findBillOfLading(mblNbr, mblOp, mblIbCv)
                            mblResponse = targetMbl == null ? "Requested MBL unavailable: Creating new." : "Requested MBL" +
                                    " already available: Existing record can be used."
                            LOGGER.debug("*** " + mblResponse + " ***")
                            mblType = (mblType != null && mblType.length() > 0) ? mblType : null
                            mblCarga = (mblCarga != null && mblCarga.length() > 0) ? mblCarga : null
                            targetMbl = targetMbl != null ? targetMbl : createNewBillOfLading(mblNbr, mblOp,
                                    UnitCategoryEnum.IMPORT, mblIbCv, mblLoadPort, mblDischPort, mblManifestedQty,
                                    mblType, mblCarga, null, targetCmplx)

                            HibernateApi.getInstance().save(targetMbl)
                        }

                        if (blNbr != null && blNbr.length() > 0) {
                            CarrierVisit blIbCv = (blcarrierVisit != null && blcarrierVisit.length() > 0) ?
                                    findCarrierVisit(targetCmplx, targetFcy, blcarrierVisit) : null
                            ScopedBizUnit blOp = (blLineOp != null && blLineOp.length() > 0) ? ScopedBizUnit
                                    .findEquipmentOperator(blLineOp) : null
                            if (blIbCv == null || blLineOp == null) {
                                LOGGER.debug("Unable to find/create BL: Invalid Carrier Visit/Line Op/Category")
                                responseMsg = responseMsg.concat(createResponse(unitId, requestType, targetFacility, "FAILURE",
                                        "Unable to find/create BL: Invalid Carrier Visit/Line Op/Category!!!"))
                                continue
                            }
                            RoutingPoint blLoadPort = RoutingPoint.findRoutingPoint(blPol)
                            RoutingPoint blDischPort = RoutingPoint.findRoutingPoint(blPod)

                            targetBl = BillOfLading.findBillOfLading(blNbr, blOp, blIbCv)
                            blResponse = targetBl == null ? "Requested BL unavailable: Creating new." : "Requested BL" +
                                    " already available: Existing record can be used."
                            LOGGER.debug("*** " + blResponse + " ***")
                            blType = (blType != null && blType.length() > 0) ? blType : null
                            blCarga = (blCarga != null && blCarga.length() > 0) ? blCarga : null
                            targetBl = targetBl != null ? targetBl : createNewBillOfLading(blNbr, blOp, UnitCategoryEnum.IMPORT,
                                    blIbCv, blLoadPort, blDischPort, blManifestedQty, blType, blCarga, blOrigin, targetCmplx)
                            if (hasMbl) {
                                LOGGER.debug("Master Bl Available : " + targetMbl)
                            }

                            HibernateApi.getInstance().save(targetBl)


                        }
                        UnitCategoryEnum unitCat = (unitCategory != null && unitCategory.length() > 0) ?
                                UnitCategoryEnum.getEnum(unitCategory) : null
                        LOGGER.debug("Requested UnitCategory : " + unitCategory)
                        LOGGER.debug("N4 UnitCategoryEnum : " + unitCat)
                        CarrierVisit ibActual = (ibActualVisit != null && ibActualVisit.length() > 0) ?
                                findCarrierVisit(targetCmplx, targetFcy, ibActualVisit) : null
                        LOGGER.debug("Requested ibActual : " + ibActualVisit)
                        LOGGER.debug("N4 ibActual : " + ibActual)
                        CarrierVisit obVisit = CarrierVisit.getGenericTruckVisit(targetCmplx)
                        LOGGER.debug("N4 obVisit : " + obVisit)
                        ScopedBizUnit unitOp = (LineOp != null && LineOp.length() > 0) ? ScopedBizUnit
                                .findEquipmentOperator(LineOp) : null
                        LOGGER.debug("Requested Line Op : " + LineOp)
                        LOGGER.debug("N4 Line Op : " + unitOp)
                        FreightKindEnum unitFrtKind = (freightKind != null && freightKind.length() > 0) ? FreightKindEnum
                                .getEnum(freightKind) : null
                        LOGGER.debug("Requested freightKind : " + freightKind)
                        LOGGER.debug("N4 freightKind : " + unitFrtKind)
                        if (unitCat == null || ibActual == null || obVisit == null || unitOp == null || unitFrtKind == null) {
                            LOGGER.debug("Invalid unit property: Category/IB Visit/OB Visit/Line Op/Frt Kind")
                            responseMsg = responseMsg.concat(createResponse(unitId, requestType, targetFacility, "FAILURE",
                                    "Invalid unit property: Category/IB Visit/OB Visit/Line Op/Frt Kind!!!"))
                            continue
                        }

                        if (equipment == null) {

                            if (isoType == null) {
                                LOGGER.debug("Invalid ISO Type requested")
                                responseMsg = responseMsg.concat(createResponse(unitId, requestType, targetFacility,
                                        "FAILURE",
                                        "Invalid ISO Type requested!!!"))
                                continue
                            }

                            targetUfv = unitManager.findOrCreatePreadvisedUnit(targetFcy, unitId, isoType, unitCat,
                                    unitFrtKind, unitOp, ibActual, obVisit, DataSourceEnum.USER_WEB, "Unit Preadvised via" +
                                    " groovy")
                        } else {
                            targetUfv = unitManager.findOrCreatePreadvisedUnit(targetFcy, equipment.getEqIdFull(), equipment
                                    .getEqEquipType(), unitCat, unitFrtKind, unitOp, ibActual, obVisit, DataSourceEnum
                                    .USER_WEB, "Unit Preadvised via groovy")
                        }
                        RoutingPoint unitLoadPort = RoutingPoint.findRoutingPoint(loadPort)
                        RoutingPoint unitDischPort = RoutingPoint.findRoutingPoint(dischargePort)

                        targetUnit = targetUfv.getUfvUnit()
                        targetUnit.getUnitRouting().setRtgPOL(unitLoadPort)
                        targetUnit.getUnitRouting().setRtgPOD1(unitDischPort)

                        if (grossWeightKg != null && grossWeightKg.length() > 0) {
                            try{
                                targetUnit.setFieldValue(UnitField.UNIT_GOODS_AND_CTR_WT_KG, grossWeightKg.toDouble())
                            }catch(BizViolation bizViolation){
                                throw bizViolation;

                            }


                        }


                        Goods unitGoods = targetUnit.getGoods()
                        LOGGER.debug("****************** unitGoods : " + unitGoods)
                        if(unitGoods!=null){
                            Hazards unitHaz = unitGoods != null && unitGoods.getGdsHazards() ? unitGoods.getGdsHazards() :
                                    Hazards.createHazardsEntity()
                            LOGGER.debug("****************** unitHaz : " + unitHaz)

                            if(unitHaz!=null){
                                while (hazIterator.hasNext()) {
                                    Element hazardnode = (Element) hazIterator.next()

                                    imdgCode = hazardnode.getAttribute("imdgCode") != null ?
                                            hazardnode.getAttribute("imdgCode").getValue() : null
                                    unNbr = hazardnode.getAttribute("unNbr") != null ? hazardnode.getAttribute("unNbr").getValue() :
                                            null
                                    LOGGER.debug("imdgCode : " + imdgCode)
                                    LOGGER.debug("unNbr : " + unNbr)

                                    if (imdgCode != null && imdgCode.length() > 0 && unNbr != null && unNbr.length() > 0) {
                                        unitHaz.addHazardItem(imdgCode, unNbr)

                                    }
                                    HibernateApi.getInstance().save(unitHaz)
                                }
                                unitGoods.attachHazards(unitHaz)
                                HibernateApi.getInstance().save(unitGoods)

                            }

                             //if (hasReeferReq) {
                            if (targetUnit.isReefer()) {
                                float tempFloat = (reqTemp.toFloat() - 32.0) * 5.0 / 9.0
                                LOGGER.debug("reqTempDouble : " + tempFloat + " C")
                                targetUnit.setFieldValue(UnitField.GDS_REEFER_RQMNTS_TEMP_REQUIRED_C, tempFloat.toDouble())
                                targetUnit.setFieldValue(InvField.UNIT_REQUIRES_POWER, Boolean.TRUE)
                                //HibernateApi.getInstance().save(targetUnit)
                            }
                        }


                        BillOfLadingManager billOfLadingManager = (BillOfLadingManager) Roastery
                                .getBean(BillOfLadingManager.BEAN_ID)
                        billOfLadingManager.assignUnitBillOfLading(targetUnit, targetBl)
                        if (targetMbl != null) {
                            targetUnit.setFieldValue(MetafieldIdFactory.valueOf("customFlexFields.unitCustomDFFblMaster"),
                                    targetMbl.getBlNbr())
                        }


                        HibernateApi.getInstance().save(targetUfv)
                        HibernateApi.getInstance().save(targetUnit)
                        HibernateApi.getInstance().flush()
                        /* responseMsg = responseMsg.concat(createResponse(unitId, requestType, targetFacility,
                                "SUCCESS", "Preadvise Unit created successfully!!!")) */
                        continue
                    }catch(Exception e){
                        LOGGER.debug("Error::"+e.stackTrace.toString())
                    }

                }

            } else if (CANCEL_PREADVISE.equalsIgnoreCase(requestType)) {
                if (targetUfv == null) {
                    LOGGER.debug("requested unit unavailable in facility")
                    responseMsg = responseMsg.concat(createResponse(unitId, requestType, targetFacility,
                            "FAILURE", "Requested Unit unavailable in target facility!!!"))

                } else {
                    Unit trgetUnit = targetUfv.getUfvUnit()
                    /*targetUfv.cancelPreadvise()
                    HibernateApi.getInstance().delete(targetUfv);
                    HibernateApi.getInstance().delete(trgetUnit);
                    HibernateApi.getInstance().flush();*/
                        unitManager.purgeUnit(targetUnit)


                    /* responseMsg = responseMsg.concat(createResponse(unitId, requestType, targetFacility,
                            "SUCCESS", "Preadvise Unit cancelled successfully!!!")) */
                    continue
                }
            }

        }

        responseMsg = responseMsg.concat("</units>")
        LOGGER.debug("Response Message : " + responseMsg)
        StringReader stringReader = new StringReader(responseMsg);
        SAXBuilder builder = new SAXBuilder()
        Document doc = builder.build(stringReader);
        Element element = doc.getRootElement()
        wsInResponse.addContent(element.detach());
        LOGGER.debug("DpwCLLCreateOrCancelPreadviseUnitWsHandler completed execution!!!!!!")
    }


    static BillOfLading createNewBillOfLading(String blNbr, ScopedBizUnit blOperator, UnitCategoryEnum blCategory,
                                              CarrierVisit blCarrierVisit, RoutingPoint blPol, RoutingPoint blPod,
                                              String blMnftQty, String blType, String blCarga, String blOrigin,
                                              Complex targetComplex) {
        BillOfLading targetBl = BillOfLading.createBillOfLading(blNbr, blOperator, blCategory, blCarrierVisit,
                DataSourceEnum.USER_WEB)
        targetBl.setFieldValue(CargoLotFields.BL_COMPLEX, targetComplex.getPrimaryKey())
        targetBl.setFieldValue(CargoLotFields.BL_POL, blPol)
        targetBl.setFieldValue(CargoLotFields.BL_POD1, blPod)
        targetBl.setFieldValue(CargoLotFields.BL_MANIFESTED_QTY, blMnftQty)
        targetBl.setFieldValue(CargoLotFields.BL_ORIGIN, blOrigin)
        targetBl.setFieldValue(MetafieldIdFactory.valueOf("customFlexFields.blCustomDFFtype"), blType)
        targetBl.setFieldValue(MetafieldIdFactory.valueOf("customFlexFields.blCustomDFFCarga"), blCarga)
        HibernateApi.getInstance().save(targetBl)
        return targetBl
    }


    static Complex findComplex(String inCpxId, Operator inOperator) {
        DomainQuery dq = QueryUtils.createDomainQuery("Complex").addDqPredicate(PredicateFactory.eq(ArgoField
                .CPX_ID, inCpxId)).addDqPredicate(PredicateFactory.eq(ArgoField.CPX_OPERATOR, inOperator.getPrimaryKey()));
        dq.setScopingEnabled(false)
        dq.setBypassInstanceSecurity(false)
        return (Complex) HibernateApi.getInstance().getUniqueEntityByDomainQuery(dq);
    }

    static Facility findFacility(String inFcyId) {
        DomainQuery dq = QueryUtils.createDomainQuery("Facility")
                .addDqPredicate(PredicateFactory.eq(ArgoField.FCY_ID, inFcyId));
        dq.setScopingEnabled(false);
        return HibernateApi.getInstance().getUniqueEntityByDomainQuery(dq);
    }


    static CarrierVisit findCarrierVisit(Complex targetComplex, Facility targetFacility, String cvId) {
        DomainQuery dq = QueryUtils.createDomainQuery("CarrierVisit")
                .addDqPredicate(PredicateFactory.eq(ArgoField.CV_COMPLEX, targetComplex.getCpxGkey()))
                .addDqPredicate(PredicateFactory.eq(ArgoField.CV_FACILITY, targetFacility.getFcyGkey()))
                .addDqPredicate(PredicateFactory.eq(ArgoField.CV_CARRIER_MODE, LocTypeEnum.VESSEL))
                .addDqPredicate(PredicateFactory.eq(ArgoField.CV_ID, cvId));
        dq.setScopingEnabled(false)
        dq.setBypassInstanceSecurity(false)
        return (CarrierVisit) HibernateApi.getInstance().getUniqueEntityByDomainQuery(dq);
    }

    public String createResponse(String unitId, String requestType, String trgtFacility, String resposeStatus, String
            message) {

        String response = """<unit>
            <containerNbr>$unitId</containerNbr>
            <requestType>$requestType</requestType>
            <targetFacility>$trgtFacility</targetFacility>
            <status>$resposeStatus</status>								
            <message>$message</message>
        </unit>"""

        return response;
    }


    private final static String REQ_PREADVISE = "CREATE_PREADVISE"
    private final static String CANCEL_PREADVISE = "CANCEL_PREADVISE"
    private final static Logger LOGGER = Logger.getLogger(DpwCLLCreateOrCancelPreadviseUnitWsHandler.class)
}
