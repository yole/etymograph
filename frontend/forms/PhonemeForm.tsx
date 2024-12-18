import {addPhoneme, updatePhoneme} from "@/api";
import EtymographForm, {EtymographFormProps} from "@/components/EtymographForm";
import FormRow from "@/components/FormRow";
import FormCheckbox from "@/components/FormCheckbox";
import {useContext} from "react";
import {GraphContext} from "@/components/Contexts";
import {PhonemeViewModel, UpdatePhonemeParameters} from "@/models";

interface PhonemeFormProps extends EtymographFormProps<UpdatePhonemeParameters, PhonemeViewModel> {
    language?: string
}

export default function PhonemeForm(props: PhonemeFormProps) {
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
