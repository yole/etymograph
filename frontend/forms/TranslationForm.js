import {addTranslation, editTranslation} from "@/api";
import EtymographForm from "@/components/EtymographForm";
import FormTextArea from "@/components/FormTextArea";
import FormRow from "@/components/FormRow";

export default function TranslationForm(props) {
    return <EtymographForm
        create={(data) => addTranslation(props.corpusTextId, data)}
        update={(data) => editTranslation(props.updateId, data)}
        {...props}
    >
        <FormTextArea rows="10" cols="50" id="text"/>
        <table><tbody>
            <FormRow label="Source" id="source"/>
        </tbody></table>
    </EtymographForm>
}
