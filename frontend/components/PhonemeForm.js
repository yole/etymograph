import {addPhoneme, updatePhoneme} from "@/api";
import EtymographForm from "@/components/EtymographForm";
import FormRow from "@/components/FormRow";
import FormCheckbox from "@/components/FormCheckbox";
import {useContext} from "react";
import {GraphContext} from "@/components/Contexts";

export default function PhonemeForm(props) {
    const graph = useContext(GraphContext)

    return <EtymographForm
        create={(data) => addPhoneme(graph, props.language, data)}
        update={(data) => updatePhoneme(graph, props.updateId, data)}
        {...props}
    >
        <table><tbody>
            <FormRow label="Graphemes" id="graphemes"/>
            <FormRow label="Sound" id="sound"/>
            <FormRow label="Classes" id="classes"/>
            <FormRow label="Source" id="source"/>
        </tbody></table>
        <FormCheckbox label="Historical" id="historical"/>
    </EtymographForm>
}
