import {addCorpusText, updateCorpusText} from "@/api";
import FormRow from "@/components/FormRow";
import EtymographForm from "@/components/EtymographForm";
import FormTextArea from "@/components/FormTextArea";

export default function CorpusTextForm(props) {
    return <EtymographForm
        create={(data) => addCorpusText(props.lang, data)}
        update={(data) => updateCorpusText(props.updateId, data)}
        {...props}
    >
        <table><tbody>
            <FormRow id="title" label="Title"/>
        </tbody></table>
        <FormTextArea rows="10" cols="50" id="text"/>
        <table>
            <tbody>
            <FormRow id="source" label="Source" />
            </tbody>
        </table>
        <h3>Notes</h3>
        <FormTextArea rows="5" cols="50" id="notes"/>
    </EtymographForm>
}
