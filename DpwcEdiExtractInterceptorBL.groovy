import com.navis.argo.business.portal.EdiExtractDao
import com.navis.argo.util.XmlUtil;
import com.navis.external.edi.entity.AbstractEdiExtractInterceptor
import com.navis.framework.SimpleSavedQueryEntity
import com.navis.framework.SimpleSavedQueryField;
import com.navis.framework.business.Roastery
import com.navis.framework.business.atoms.PredicateParmEnum
import com.navis.framework.business.atoms.PredicateVerbEnum
import com.navis.framework.metafields.MetafieldId;
import com.navis.framework.metafields.MetafieldIdFactory;
import com.navis.framework.portal.QueryUtils;
import com.navis.framework.portal.query.DomainQuery;
import com.navis.framework.portal.query.PredicateFactory
import com.navis.framework.query.business.SavedPredicate
import com.navis.framework.util.ValueHolder
import com.navis.framework.util.ValueObject;
import com.navis.inventory.business.units.Unit;
import com.navis.services.ServicesField;
import com.navis.services.business.event.Event;
import com.navis.services.business.rules.EventType
import org.apache.log4j.Level
import org.apache.log4j.Logger;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jdom.Document;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.*;

public class DpwcEdiExtractInterceptorBL extends AbstractEdiExtractInterceptor {
    private String BL_MASTER_FIELD = "customFlexFields.unitCustomDFFblMaster";
    private String EDI_CONTAINER_ELEMENT = "ediContainer";

    private SavedPredicate _savedPredicate;


    public SavedPredicate get_savedPredicate() {
        return _savedPredicate;
    }

    public void set_savedPredicate(SavedPredicate _savedPredicate) {
        this._savedPredicate = _savedPredicate;
    }

    @Override
    public Element beforeEdiMap(Map inParams) {
        Element tranElement = (Element) inParams.get("XML_TRANSACTION");
        log("tranElement:[" + tranElement + "] inParams:[" + inParams + "]  children:[" + tranElement.getChildren() + "]");

        Event event = (Event) inParams.get("ENTITY");
        Unit unit = findUnitFromEvent(event);
        if(unit==null) return tranElement;

        replaceBlMasterSegment(tranElement, unit);
        //replaceDtmSegment(tranElement, unit);

        return tranElement;
    }

    @Override
    void beforeEdiExtract(Map inParams) {
        SavedPredicate savedPredicate = (SavedPredicate) inParams.get("PREDICATE");
        if (savedPredicate != null) {
            ValueObject vaoAll = createVaoEntry(0, null, PredicateVerbEnum.AND, null);
            List savedPredicateList = savedPredicate.getPrdctChildPrdctList()
            ValueHolder[] childPredicates;
            if (savedPredicateList != null && !savedPredicateList.isEmpty()) {
                childPredicates = new ValueHolder[savedPredicateList.size() + 1];
                int i = 0;
                SavedPredicate predicateChild;

                for (Iterator iterator$ = savedPredicateList.iterator(); iterator$.hasNext(); childPredicates[i++] = predicateChild.getPredicateVao()) {
                    Object childObj = iterator$.next();
                    predicateChild = (SavedPredicate) childObj;
                }

                childPredicates[savedPredicateList.size()] = createVaoEntry(savedPredicateList.size(), MetafieldIdFactory.valueOf("ufvUnit.ufvFlexString02"), PredicateVerbEnum.NE, "Y");

            }else {
                childPredicates = new ValueHolder[1];
                childPredicates[0] = createVaoEntry(0, MetafieldIdFactory.valueOf("ufvUnit.ufvFlexString02"), PredicateVerbEnum.NE, "Y");

            }

            vaoAll.setFieldValue(SimpleSavedQueryField.PRDCT_CHILD_PRDCT_LIST, childPredicates);

            SavedPredicate newSavedPredicate = new SavedPredicate(vaoAll);
            set_savedPredicate(newSavedPredicate);
        }

    }

    private void replaceBlMasterSegment(Element tranElement, Unit unit) {
        String prefixLog = "    SetBlMaster -> UnitId[" + unit.getUnitId() + "] UnitGkey:[" + unit.getUnitGkey() + "] ";
        Element ediContainer = tranElement.getChild(EDI_CONTAINER_ELEMENT, XmlUtil.ARGO_NAMESPACE);
        log(prefixLog + "ediContainer: " + ediContainer + "] ");


        String blMaster = findBlMaster(unit);
        log(prefixLog + " blMaster:[" + blMaster + "]");

       //Adding the element
        Element ediFlexField=tranElement.getChild("ediFlexFields",XmlUtil.ARGO_NAMESPACE)
        Element unitFlexString03element=new Element("unitFlexString03",ediFlexField.getNamespace())
        unitFlexString03element.setText(blMaster)
        ediFlexField.addContent(unitFlexString03element)


        /*Attribute attribute1=new Attribute("unitFlexString03",blMaster,XmlUtil.ARGO_NAMESPACE)
        ediFlexField.setAttribute(attribute1)
*/

        Attribute attribute = new Attribute(BL_MASTER_FIELD, blMaster, XmlUtil.ARGO_NAMESPACE);
        ediContainer.setAttribute(attribute);

    }
    private String findBlMaster(Unit unit){
        if(unit==null) return null;
        log("flex: " + unit.getCustomFlexFields());
        String blMaster =  (String)unit.getField(MetafieldIdFactory.valueOf(BL_MASTER_FIELD));
        log("unit: " + unit + " bl_master: " + blMaster);
        return blMaster!=null ? blMaster : "";
    }

    private Unit findUnitFromEvent(Event event) {
        log("   Event:[" + event + "]");
        if(event==null) return null;

        Unit unit = Unit.hydrate(event.getEvntAppliedToPrimaryKey());
        String eventTypeId = event.getEventTypeId();
        log("   Event:[" + event + "] Unit:["+unit+"] EventTypeId:["+eventTypeId+"]");
        return unit;
    }
    private ValueObject createVaoEntry(long inOrder, MetafieldId inMetafieldId, PredicateVerbEnum inVerbEnum, Object inValue) {
        ValueObject vao = new ValueObject(SimpleSavedQueryEntity.SAVED_PREDICATE);
        vao.setFieldValue(SimpleSavedQueryField.PRDCT_METAFIELD, inMetafieldId);
        vao.setFieldValue(SimpleSavedQueryField.PRDCT_VERB, inVerbEnum);
        vao.setFieldValue(SimpleSavedQueryField.PRDCT_VALUE, inValue);
        vao.setFieldValue(SimpleSavedQueryField.PRDCT_UI_VALUE, null);
        vao.setFieldValue(SimpleSavedQueryField.PRDCT_ORDER, new Long(inOrder));
        vao.setFieldValue(SimpleSavedQueryField.PRDCT_NEGATED, Boolean.FALSE);
        vao.setFieldValue(SimpleSavedQueryField.PRDCT_PARAMETER_TYPE, PredicateParmEnum.NO_PARM);
        return vao;

    }

    @Override
    public ValueHolder savedPredicate() {
        return get_savedPredicate() != null ? get_savedPredicate().getPredicateVao() : null;
    }
}

