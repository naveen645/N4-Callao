import com.navis.argo.business.atoms.UnitCategoryEnum
import com.navis.cargo.business.model.BillOfLading
import com.navis.external.services.AbstractGeneralNoticeCodeExtension
import com.navis.framework.persistence.HibernateApi
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.road.business.model.TruckTransaction
import com.navis.services.business.event.GroovyEvent
import com.navis.services.business.rules.EventType
import org.apache.log4j.Level
import org.apache.log4j.Logger

class UnitBlMscGeneralNoticeCode extends AbstractGeneralNoticeCodeExtension {
    @Override
    void execute(GroovyEvent inGroovyEvent) {
        LOGGER.setLevel(Level.DEBUG)
        LOGGER.info("UnitBlMscGeneralNoticeCode started execution!!!!!!!")
        Unit unit=(Unit)inGroovyEvent.getEntity()
        String unitLineOperator=unit.getUnitLineOperator().getBzuId()
        String blNbr=unit.getUnitGoods().getGdsBlNbr()
        BillOfLading billOfLading=null
        if(UnitCategoryEnum.EXPORT.equals(unit.getUnitCategory())){
            billOfLading=BillOfLading.findBillOfLading(blNbr,unit.getOutboundCv())
        }else if(UnitCategoryEnum.IMPORT.equals(unit.getUnitCategory())){
            billOfLading=BillOfLading.findBillOfLading(blNbr,unit.getInboundCv())
        }
        String blLineOperator=billOfLading.getBlLineOperator().getBzuId()
        if(!unitLineOperator.equals(blLineOperator)){
            UnitFacilityVisit ufv = unit.getUnitActiveUfvNowActive()
            if(ufv.getUfvFlexString01()==null){
                ufv.setUfvFlexString01("Y")
                HibernateApi.getInstance().save(ufv)
            }
        }
    }
    private final static Logger LOGGER = Logger.getLogger(UnitBlMscGeneralNoticeCode.class)
}
