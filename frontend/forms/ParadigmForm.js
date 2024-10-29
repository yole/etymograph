import EtymographForm from "@/components/EtymographForm";
import FormRow from "@/components/FormRow";
import FormTextArea from "@/components/FormTextArea";
import {addParadigm, updateParadigm} from "@/api";
import {useContext} from "react";
import {GraphContext} from "@/components/Contexts";
import PosSelect from "@/components/PosSelect";

export default function ParadigmForm(props) {
    const graph = useContext(GraphContext)
    return <EtymographForm
        create={(data) => addParadigm(graph, data.name, props.lang, data.pos, data.text)}
        update={(data) => updateParadigm(graph, props.updateId, data.name, data.pos, data.text)}
        {...props}
    >
        <table><tbody>
            <FormRow label="Name" id="name"/>
            <PosSelect label="POS" id="pos" language={props.lang} isMulti={true}/>
        </tbody></table>
        <FormTextArea rows="10" cols="80" id="text"/>
    </EtymographForm>
}
