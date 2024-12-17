import EtymographForm from "@/components/EtymographForm";
import FormRow from "@/components/FormRow";
import FormTextArea from "@/components/FormTextArea";
import {addParadigm, updateParadigm} from "@/api";
import {useContext} from "react";
import {GraphContext} from "@/components/Contexts";
import PosSelect from "@/components/PosSelect";
import RuleListSelect from "@/components/RuleListSelect";

export default function ParadigmForm(props) {
    const graph = useContext(GraphContext)
    return <EtymographForm
        create={(data) => addParadigm(graph, props.lang, data)}
        update={(data) => updateParadigm(graph, props.updateId, data)}
        {...props}
    >
        <table><tbody>
            <FormRow label="Name" id="name"/>
            <PosSelect label="POS" id="pos" language={props.lang} isMulti={true}/>
            <RuleListSelect label="Pre rule" id="preRuleName" language={props.lang} isMulti={false} showNone={true}/>
            <RuleListSelect label="Post rule" id="postRuleName" language={props.lang} isMulti={false} showNone={true}/>
        </tbody></table>
        <FormTextArea rows={10} cols={80} id="text"/>
    </EtymographForm>
}
