import RuleSequenceForm from "@/forms/RuleSequenceForm";
import Breadcrumbs from "@/components/Breadcrumbs";

export default function RuleSequenceEditor() {
    return <>
        <Breadcrumbs title="New Rule Sequence"/>
        <RuleSequenceForm redirectOnCreate={(data) => `/rules/${data.toLang}`}/>
    </>
}
